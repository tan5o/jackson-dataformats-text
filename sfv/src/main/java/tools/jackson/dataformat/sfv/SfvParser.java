package tools.jackson.dataformat.sfv;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.io.IOContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class SfvParser {
    private static final int MAX_CHARS_TO_REPORT = 1000;

        public static JsonNode parse(ObjectReadContext readCtxt, IOContext ioContext, int formatReadFeatures,
            SfvType type, Reader reader) throws IOException {
        String input = readAll(reader);
        try {
            Parser parser = new Parser(input, readCtxt);
            JsonNode result;
            switch (type) {
            case ITEM:
                result = parser.parseItem();
                break;
            case LIST:
                result = parser.parseList();
                break;
            case DICTIONARY:
                result = parser.parseDictionary();
                break;
            default:
                throw new IllegalArgumentException("Unsupported SFV type: " + type);
            }
            parser.skipOWS();
            if (parser.peek('\t')) {
                throw parser.error("Invalid whitespace");
            }
            if (!parser.isEof()) {
                throw parser.error("Trailing data");
            }
            return result;
        } catch (ParseFailure e) {
            String msg = e.getMessage();
            if (msg == null) {
                msg = "Invalid Structured Field Value";
            }
            String snippet = input;
            if (snippet.length() > MAX_CHARS_TO_REPORT) {
                snippet = snippet.substring(0, MAX_CHARS_TO_REPORT) + "...";
            }
            throw new StreamReadException((tools.jackson.core.JsonParser) null, msg + " in input: " + snippet, e);
        }
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[1024];
        int len;
        while ((len = reader.read(buffer)) > 0) {
            sb.append(buffer, 0, len);
        }
        return sb.toString();
    }

    private static final class ParseFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ParseFailure(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private static final String TYPE_FIELD = "__type";
        private static final String VALUE_FIELD = "value";

        private final String input;
        private final ObjectReadContext ctxt;
        private int pos;

        Parser(String input, ObjectReadContext ctxt) {
            this.input = input;
            this.ctxt = ctxt;
        }

        JsonNode parseDictionary() {
            Map<String, JsonNode> members = new LinkedHashMap<>();
            skipOWS();
            if (peek('\t')) {
                throw error("Invalid whitespace");
            }
            if (isEof()) {
                return JsonNodeFactory.instance.arrayNode();
            }
            while (true) {
                String key = parseKey();
                if (peek('=') ) {
                    next();
                    if (peek(' ') || peek('\t')) {
                        throw error("Unexpected whitespace after '='");
                    }
                    ListElement value = parseListElement();
                    members.put(key, value.node);
                } else if (peek(';') || peek(',') || isEof()) {
                    ArrayNode item = JsonNodeFactory.instance.arrayNode();
                    item.add(JsonNodeFactory.instance.booleanNode(true));
                    item.add(parseParameters());
                    members.put(key, item);
                } else if (peek(' ') || peek('\t')) {
                    throw error("Unexpected whitespace before '='");
                } else {
                    throw error("Invalid dictionary member");
                }
                skipOWSWithTabs();
                if (peek(',')) {
                    next();
                    skipOWSWithTabs();
                    if (isEof()) {
                        throw error("Trailing comma");
                    }
                    continue;
                }
                break;
            }
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (Map.Entry<String, JsonNode> entry : members.entrySet()) {
                ArrayNode pair = JsonNodeFactory.instance.arrayNode();
                pair.add(entry.getKey());
                pair.add(entry.getValue());
                result.add(pair);
            }
            return result;
        }

        JsonNode parseList() {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            skipOWS();
            if (peek('\t')) {
                throw error("Invalid whitespace");
            }
            if (isEof()) {
                return result;
            }
            while (true) {
                ListElement element = parseListElement();
                result.add(element.node);
                skipOWSWithTabs();
                if (peek(',')) {
                    next();
                    skipOWSWithTabs();
                    if (isEof()) {
                        throw error("Trailing comma");
                    }
                    continue;
                }
                break;
            }
            return result;
        }

        JsonNode parseItem() {
            skipOWS();
            if (peek('\t')) {
                throw error("Invalid whitespace");
            }
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            result.add(parseBareItem());
            result.add(parseParameters());
            return result;
        }

        private ListElement parseListElement() {
            if (peek('(')) {
                return new ListElement(parseInnerList());
            }
            return new ListElement(parseItem());
        }

        private JsonNode parseInnerList() {
            expect('(');
            // Skip optional leading SP after '('
            skipOWS();
            ArrayNode items = JsonNodeFactory.instance.arrayNode();
            // Check if there's content before ')'
            if (!peek(')')) {
                // Parse first item
                items.add(parseItem());
                // Loop: while there are spaces, parse more items
                while (!isEof()) {
                    // Check for spaces before next item or before ')'
                    if (!peek(' ')) {
                        break; // No space, must be ')' or invalid
                    }
                    // Consume all spaces
                    consumeSpaces();
                    // After spaces, if ')', we're done (trailing spaces allowed)
                    if (peek(')')) {
                        break;
                    }
                    // Parse next item
                    items.add(parseItem());
                }
            }
            expect(')');
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            result.add(items);
            result.add(parseParameters());
            return result;
        }

        private ArrayNode parseParameters() {
            // Use LinkedHashMap to maintain order and handle duplicate keys (last value wins)
            java.util.LinkedHashMap<String, JsonNode> paramsMap = new java.util.LinkedHashMap<>();
            while (peek(';')) {
                next();
                // RFC 9651: skip SP characters (but not Tab) after semicolon
                skipOWS();
                String key = parseKey();
                JsonNode value;
                if (peek('=')) {
                    next();
                    value = parseBareItem();
                } else {
                    value = JsonNodeFactory.instance.booleanNode(true);
                }
                paramsMap.put(key, value);
            }
            ArrayNode params = JsonNodeFactory.instance.arrayNode();
            for (java.util.Map.Entry<String, JsonNode> e : paramsMap.entrySet()) {
                ArrayNode entry = JsonNodeFactory.instance.arrayNode();
                entry.add(e.getKey());
                entry.add(e.getValue());
                params.add(entry);
            }
            return params;
        }

        private JsonNode parseBareItem() {
            if (peek('"')) {
                return JsonNodeFactory.instance.textNode(parseString());
            }
            if (peek('%')) {
                return parseDisplayString();
            }
            if (peek('?')) {
                return parseBoolean();
            }
            if (peek('@')) {
                return parseDate();
            }
            if (peek(':')) {
                return parseBinary();
            }
            char ch = peek();
            if (ch == '-' || isDigit(ch)) {
                return parseNumber();
            }
            if (isTokenChar(ch)) {
                return parseToken();
            }
            throw error("Invalid bare item");
        }

        private JsonNode parseBoolean() {
            expect('?');
            char v = next();
            if (v == '1') {
                return JsonNodeFactory.instance.booleanNode(true);
            }
            if (v == '0') {
                return JsonNodeFactory.instance.booleanNode(false);
            }
            throw error("Invalid boolean");
        }

        private JsonNode parseDate() {
            expect('@');
            int start = pos;
            if (peek('-')) {
                next();
            }
            if (!isDigit(peek())) {
                throw error("Invalid date value");
            }
            while (!isEof() && isDigit(peek())) {
                next();
            }
            String value = input.substring(start, pos);
            long seconds;
            try {
                seconds = Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw error("Invalid date value");
            }
            ObjectNode obj = JsonNodeFactory.instance.objectNode();
            obj.put(TYPE_FIELD, "date");
            obj.put(VALUE_FIELD, seconds);
            return obj;
        }

        private JsonNode parseNumber() {
            int start = pos;
            boolean negative = false;
            if (peek('-')) {
                negative = true;
                next();
            }
            if (!isDigit(peek())) {
                throw error("Invalid number");
            }
            int intStart = pos;
            while (!isEof() && isDigit(peek())) {
                next();
            }
            int intLen = pos - intStart;
            
            boolean decimal = false;
            int fracLen = 0;
            if (peek('.')) {
                decimal = true;
                next();
                int fracStart = pos;
                while (!isEof() && isDigit(peek())) {
                    next();
                }
                fracLen = pos - fracStart;
                if (fracLen == 0 || fracLen > 3) {
                    throw error("Invalid decimal fraction");
                }
                // RFC 9651: decimal integer part max 12 digits
                if (intLen > 12) {
                    throw error("Decimal integer part exceeds 12 digits");
                }
            } else {
                // RFC 9651: integer max 15 digits
                if (intLen > 15) {
                    throw error("Integer exceeds 15 digits");
                }
            }
            
            String raw = input.substring(start, pos);
            if (decimal) {
                // Normalize decimal by stripping trailing zeros for comparison with test JSON
                java.math.BigDecimal bd = new java.math.BigDecimal(raw).stripTrailingZeros();
                // Use double for values that fit to match JSON test file expectations
                double d = bd.doubleValue();
                if (new java.math.BigDecimal(d).compareTo(bd) == 0) {
                    return JsonNodeFactory.instance.numberNode(d);
                }
                return JsonNodeFactory.instance.numberNode(bd);
            }
            long value;
            try {
                value = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw error("Invalid integer value");
            }
            // RFC 9651: integer range check
            if (value < -999_999_999_999_999L || value > 999_999_999_999_999L) {
                throw error("Integer out of range");
            }
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return JsonNodeFactory.instance.numberNode((int) value);
            }
            return JsonNodeFactory.instance.numberNode(value);
        }

        private JsonNode parseToken() {
            String token = parseTokenString();
            ObjectNode obj = JsonNodeFactory.instance.objectNode();
            obj.put(TYPE_FIELD, "token");
            obj.put(VALUE_FIELD, token);
            return obj;
        }

        private JsonNode parseBinary() {
            expect(':');
            int start = pos;
            while (!isEof() && peek() != ':') {
                char c = next();
                if (!isBase64Char(c)) {
                    throw error("Invalid binary content");
                }
            }
            expect(':');
            String value = input.substring(start, pos - 1);
            String base32;
            try {
                base32 = SfvCodec.binaryToJsonValue(value);
            } catch (IllegalArgumentException e) {
                throw error("Invalid binary content");
            }
            ObjectNode obj = JsonNodeFactory.instance.objectNode();
            obj.put(TYPE_FIELD, "binary");
            obj.put(VALUE_FIELD, base32);
            return obj;
        }

        private JsonNode parseDisplayString() {
            expect('%');
            expect('"');
            String value = parseDisplayStringContent();
            ObjectNode obj = JsonNodeFactory.instance.objectNode();
            obj.put(TYPE_FIELD, "displaystring");
            obj.put(VALUE_FIELD, value);
            return obj;
        }

        private String parseDisplayStringContent() {
            // RFC 9651: display-string = "%" DQUOTE *(byte-or-pct) DQUOTE
            // byte-or-pct = %x20-24 / "%25" / %x26-5B / "\\" / "\\\"" / %x5D-7E / pct-encoded
            // pct-encoded = "%" LCHEXDIG LCHEXDIG
            // Note: %25 is percent itself
            // Note: Per httpwg test suite, backslash is also allowed as a literal character
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            while (!isEof()) {
                char c = peek();
                if (c == '"') {
                    next();
                    byte[] raw = bytes.toByteArray();
                    // Validate UTF-8
                    if (!isValidUtf8(raw)) {
                        throw error("Invalid UTF-8 sequence in display string");
                    }
                    try {
                        return new String(raw, "UTF-8");
                    } catch (java.io.UnsupportedEncodingException e) {
                        throw error("Invalid UTF-8 in display string");
                    }
                }
                if (c == '\\') {
                    next();
                    if (!isEof()) {
                        char esc = peek();
                        if (esc == '"' || esc == '\\') {
                            // Valid escape sequence
                            next();
                            bytes.write(esc);
                            continue;
                        }
                    }
                    // Backslash not followed by " or \ - treat as literal backslash
                    bytes.write('\\');
                    continue;
                }
                if (c == '%') {
                    next();
                    // Percent-encoding: %XX where XX is lowercase hex
                    if (isEof() || !isLowerHex(peek())) {
                        throw error("Invalid percent-encoding in display string");
                    }
                    char h1 = next();
                    if (isEof() || !isLowerHex(peek())) {
                        throw error("Invalid percent-encoding in display string");
                    }
                    char h2 = next();
                    int byteVal = (hexValue(h1) << 4) | hexValue(h2);
                    bytes.write(byteVal);
                    continue;
                }
                // Valid printable ASCII except " and %
                // Note: Backslash is handled above, so we exclude it here to avoid double processing
                if (c >= 0x20 && c <= 0x7E && c != '"' && c != '%' && c != '\\') {
                    next();
                    bytes.write(c);
                    continue;
                }
                throw error("Invalid character in display string");
            }
            throw error("Unterminated display string");
        }

        /**
         * Validate UTF-8 byte sequence according to RFC 3629.
         */
        private static boolean isValidUtf8(byte[] bytes) {
            int i = 0;
            while (i < bytes.length) {
                int b = bytes[i] & 0xFF;
                if (b <= 0x7F) {
                    // ASCII - single byte
                    i++;
                } else if ((b & 0xE0) == 0xC0) {
                    // 2-byte sequence: 110xxxxx 10xxxxxx
                    if (b < 0xC2) return false; // Overlong encoding
                    if (i + 1 >= bytes.length) return false;
                    int b2 = bytes[i + 1] & 0xFF;
                    if ((b2 & 0xC0) != 0x80) return false;
                    i += 2;
                } else if ((b & 0xF0) == 0xE0) {
                    // 3-byte sequence: 1110xxxx 10xxxxxx 10xxxxxx
                    if (i + 2 >= bytes.length) return false;
                    int b2 = bytes[i + 1] & 0xFF;
                    int b3 = bytes[i + 2] & 0xFF;
                    if ((b2 & 0xC0) != 0x80) return false;
                    if ((b3 & 0xC0) != 0x80) return false;
                    // Check for overlong encoding
                    if (b == 0xE0 && b2 < 0xA0) return false;
                    // Check for surrogates (U+D800-U+DFFF)
                    if (b == 0xED && b2 >= 0xA0) return false;
                    i += 3;
                } else if ((b & 0xF8) == 0xF0) {
                    // 4-byte sequence: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                    if (b > 0xF4) return false; // Beyond U+10FFFF
                    if (i + 3 >= bytes.length) return false;
                    int b2 = bytes[i + 1] & 0xFF;
                    int b3 = bytes[i + 2] & 0xFF;
                    int b4 = bytes[i + 3] & 0xFF;
                    if ((b2 & 0xC0) != 0x80) return false;
                    if ((b3 & 0xC0) != 0x80) return false;
                    if ((b4 & 0xC0) != 0x80) return false;
                    // Check for overlong encoding
                    if (b == 0xF0 && b2 < 0x90) return false;
                    // Check for values beyond U+10FFFF
                    if (b == 0xF4 && b2 > 0x8F) return false;
                    i += 4;
                } else {
                    // Invalid start byte
                    return false;
                }
            }
            return true;
        }

        private static boolean isLowerHex(char c) {
            return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
        }

        private static int hexValue(char c) {
            if (c >= '0' && c <= '9') return c - '0';
            return c - 'a' + 10;
        }

        private String parseString() {
            expect('"');
            return parseQuotedString();
        }

        private String parseQuotedString() {
            StringBuilder sb = new StringBuilder();
            while (!isEof()) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (isEof()) {
                        throw error("Invalid escape");
                    }
                    char esc = next();
                    if (esc != '"' && esc != '\\') {
                        throw error("Invalid escape");
                    }
                    sb.append(esc);
                    continue;
                }
                if (c < 0x20 || c > 0x7E) {
                    throw error("Invalid character in string");
                }
                sb.append(c);
            }
            throw error("Unterminated string");
        }

        private String parseTokenString() {
            int start = pos;
            char ch = next();
            // RFC 9651: token = ( ALPHA / "*" ) *( tchar / ":" / "/" )
            // Token must START with ALPHA or *
            if (!isAlpha(ch) && ch != '*') {
                throw error("Invalid token start character");
            }
            while (!isEof() && isTokenChar(peek())) {
                next();
            }
            return input.substring(start, pos);
        }

        private String parseKey() {
            int start = pos;
            char ch = next();
            if (!isKeyStart(ch)) {
                throw error("Invalid key");
            }
            while (!isEof() && isKeyChar(peek())) {
                next();
            }
            return input.substring(start, pos);
        }

        void skipOWS() {
            while (!isEof() && peek() == ' ') {
                pos++;
            }
        }

        void skipOWSWithTabs() {
            while (!isEof()) {
                char c = peek();
                if (c == ' ' || c == '\t') {
                    pos++;
                    continue;
                }
                break;
            }
        }

        private void consumeSpaces() {
            if (!peek(' ')) {
                throw error("Expected space");
            }
            while (!isEof() && peek() == ' ') {
                next();
            }
        }

        boolean isEof() {
            return pos >= input.length();
        }

        char peek() {
            return isEof() ? '\0' : input.charAt(pos);
        }

        boolean peek(char c) {
            return !isEof() && input.charAt(pos) == c;
        }

        char next() {
            if (isEof()) {
                throw error("Unexpected end of input");
            }
            return input.charAt(pos++);
        }

        void expect(char c) {
            if (!peek(c)) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }

        ParseFailure error(String message) {
            return new ParseFailure(message + " at position " + pos);
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isAlpha(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private static boolean isKeyStart(char c) {
            // RFC 9651: key = ( lcalpha / "*" ) *( lcalpha / DIGIT / "_" / "-" / "." / "*" )
            return (c >= 'a' && c <= 'z') || c == '*';
        }

        private static boolean isKeyChar(char c) {
            return isKeyStart(c) || isDigit(c) || c == '_' || c == '-' || c == '.' || c == '*';
        }

        private static boolean isTokenChar(char c) {
            // RFC 9651: token = ( ALPHA / "*" ) *( tchar / ":" / "/" )
            // tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
            //         "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
            return isAlpha(c) || isDigit(c)
                    || c == '!' || c == '#' || c == '$' || c == '%' || c == '&' || c == '\''
                    || c == '*' || c == '+' || c == '-' || c == '.' || c == '^' || c == '_'
                    || c == '`' || c == '|' || c == '~' || c == ':' || c == '/';
        }

        private static boolean isBase64Char(char c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=';
        }
    }

    private static final class ListElement {
        final JsonNode node;

        ListElement(JsonNode node) {
            this.node = node;
        }
    }
}
