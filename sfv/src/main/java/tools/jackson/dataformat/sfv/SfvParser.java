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
            skipOWS();
            ArrayNode items = JsonNodeFactory.instance.arrayNode();
            if (!peek(')')) {
                while (true) {
                    items.add(parseItem());
                    if (peek(')')) {
                        break;
                    }
                    if (!peek(' ')) {
                        throw error("Expected space between inner list items");
                    }
                    consumeSpaces();
                }
            }
            expect(')');
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            result.add(items);
            result.add(parseParameters());
            return result;
        }

        private ArrayNode parseParameters() {
            ArrayNode params = JsonNodeFactory.instance.arrayNode();
            while (peek(';')) {
                next();
                String key = parseKey();
                JsonNode value;
                if (peek('=')) {
                    next();
                    value = parseBareItem();
                } else {
                    value = JsonNodeFactory.instance.booleanNode(true);
                }
                ArrayNode entry = JsonNodeFactory.instance.arrayNode();
                entry.add(key);
                entry.add(value);
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
            if (peek('-')) {
                next();
            }
            if (!isDigit(peek())) {
                throw error("Invalid number");
            }
            while (!isEof() && isDigit(peek())) {
                next();
            }
            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                next();
                int fracStart = pos;
                while (!isEof() && isDigit(peek())) {
                    next();
                }
                int fracLen = pos - fracStart;
                if (fracLen == 0 || fracLen > 3) {
                    throw error("Invalid decimal fraction");
                }
            }
            String raw = input.substring(start, pos);
            if (decimal) {
                return JsonNodeFactory.instance.numberNode(new java.math.BigDecimal(raw));
            }
            long value = Long.parseLong(raw);
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
            String value = parseQuotedString('%');
            ObjectNode obj = JsonNodeFactory.instance.objectNode();
            obj.put(TYPE_FIELD, "displaystring");
            obj.put(VALUE_FIELD, value);
            return obj;
        }

        private String parseString() {
            expect('"');
            return parseQuotedString('"');
        }

        private String parseQuotedString(char terminator) {
            StringBuilder sb = new StringBuilder();
            while (!isEof()) {
                char c = next();
                if (c == terminator) {
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
                if (c < 0x20 || c == 0x7F) {
                    throw error("Invalid character in string");
                }
                sb.append(c);
            }
            throw error("Unterminated string");
        }

        private String parseTokenString() {
            int start = pos;
            char ch = next();
            if (!isTokenChar(ch)) {
                throw error("Invalid token");
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
            return (c >= 'a' && c <= 'z');
        }

        private static boolean isKeyChar(char c) {
            return isKeyStart(c) || isDigit(c) || c == '_' || c == '-' || c == '.' || c == '*';
        }

        private static boolean isTokenChar(char c) {
            return isAlpha(c) || isDigit(c) || c == '_' || c == '-' || c == '.' || c == '*' || c == '/';
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
