package tools.jackson.dataformat.sfv;

import java.math.BigDecimal;
import java.util.Base64;

import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class SfvCodec {
    private static final String TYPE_FIELD = "__type";
    private static final String VALUE_FIELD = "value";

    private SfvCodec() { }

    static String write(JsonNode node, SfvType type) throws StreamWriteException {
        StringBuilder out = new StringBuilder();
        try {
            switch (type) {
            case ITEM:
                writeItem(node, out);
                break;
            case LIST:
                writeList(node, out);
                break;
            case DICTIONARY:
                writeDictionary(node, out);
                break;
            default:
                throw new IllegalArgumentException("Unsupported SFV type: " + type);
            }
        } catch (IllegalArgumentException e) {
            throw new StreamWriteException(null, e.getMessage(), e);
        }
        return out.toString();
    }

    private static void writeDictionary(JsonNode node, StringBuilder out) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("Dictionary must be an array of pairs");
        }
        boolean first = true;
        for (JsonNode entryNode : node) {
            if (!entryNode.isArray() || entryNode.size() != 2) {
                throw new IllegalArgumentException("Dictionary entry must be a 2-element array");
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            JsonNode keyNode = entryNode.get(0);
            if (!keyNode.isTextual()) {
                throw new IllegalArgumentException("Dictionary key must be a string");
            }
            String key = keyNode.textValue();
            validateKey(key);
            out.append(key);
            JsonNode valueNode = entryNode.get(1);
            if (isTrueItem(valueNode)) {
                writeParameters(valueNode.get(1), out);
            } else {
                out.append('=');
                writeListElement(valueNode, out);
            }
        }
    }

    private static void writeList(JsonNode node, StringBuilder out) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("List must be an array");
        }
        boolean first = true;
        for (JsonNode elementNode : node) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeListElement(elementNode, out);
        }
    }

    private static void writeListElement(JsonNode node, StringBuilder out) {
        if (!node.isArray() || node.size() != 2) {
            throw new IllegalArgumentException("List element must be a 2-element array");
        }
        JsonNode first = node.get(0);
        if (first.isArray()) {
            writeInnerList(node, out);
        } else {
            writeItem(node, out);
        }
    }

    private static void writeInnerList(JsonNode node, StringBuilder out) {
        JsonNode itemsNode = node.get(0);
        JsonNode paramsNode = node.get(1);
        if (!itemsNode.isArray()) {
            throw new IllegalArgumentException("Inner list items must be an array");
        }
        out.append('(');
        boolean first = true;
        for (JsonNode itemNode : itemsNode) {
            if (!first) {
                out.append(' ');
            }
            first = false;
            writeItem(itemNode, out);
        }
        out.append(')');
        writeParameters(paramsNode, out);
    }

    private static void writeItem(JsonNode node, StringBuilder out) {
        if (!node.isArray() || node.size() != 2) {
            throw new IllegalArgumentException("Item must be a 2-element array");
        }
        writeBareItem(node.get(0), out);
        writeParameters(node.get(1), out);
    }

    private static void writeParameters(JsonNode node, StringBuilder out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Parameters must be an array");
        }
        for (JsonNode entryNode : node) {
            if (!entryNode.isArray() || entryNode.size() != 2) {
                throw new IllegalArgumentException("Parameter entry must be a 2-element array");
            }
            JsonNode keyNode = entryNode.get(0);
            if (!keyNode.isTextual()) {
                throw new IllegalArgumentException("Parameter key must be a string");
            }
            String key = keyNode.textValue();
            validateKey(key);
            out.append(';').append(key);
            JsonNode valueNode = entryNode.get(1);
            if (!valueNode.isBoolean() || !valueNode.booleanValue()) {
                out.append('=');
                writeBareItem(valueNode, out);
            }
        }
    }

    private static void writeBareItem(JsonNode node, StringBuilder out) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Bare item cannot be null");
        }
        if (node.isObject()) {
            writeTypedItem((ObjectNode) node, out);
            return;
        }
        if (node.isTextual()) {
            writeString(node.textValue(), out);
            return;
        }
        if (node.isBoolean()) {
            out.append(node.booleanValue() ? "?1" : "?0");
            return;
        }
        if (node.isIntegralNumber()) {
            out.append(node.longValue());
            return;
        }
        if (node.isNumber()) {
            writeDecimal(node.decimalValue(), out);
            return;
        }
        throw new IllegalArgumentException("Unsupported bare item value");
    }

    private static void writeTypedItem(ObjectNode node, StringBuilder out) {
        JsonNode typeNode = node.get(TYPE_FIELD);
        JsonNode valueNode = node.get(VALUE_FIELD);
        if (typeNode == null || !typeNode.isTextual()) {
            throw new IllegalArgumentException("Typed item must contain a string '__type'");
        }
        if (valueNode == null) {
            throw new IllegalArgumentException("Typed item must contain a 'value'");
        }
        String type = typeNode.textValue();
        if ("token".equals(type)) {
            String token = valueNode.textValue();
            validateToken(token);
            out.append(token);
            return;
        }
        if ("binary".equals(type)) {
            String value = valueNode.textValue();
            byte[] bytes = decodeBase32(value);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            out.append(':').append(base64).append(':');
            return;
        }
        if ("date".equals(type)) {
            out.append('@').append(valueNode.longValue());
            return;
        }
        if ("displaystring".equals(type)) {
            writeDisplayString(valueNode.textValue(), out);
            return;
        }
        throw new IllegalArgumentException("Unsupported __type: " + type);
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
                continue;
            }
            if (c < 0x20 || c == 0x7F) {
                throw new IllegalArgumentException("Invalid character in string");
            }
            out.append(c);
        }
        out.append('"');
    }

    private static void writeDisplayString(String value, StringBuilder out) {
        out.append('%').append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
                continue;
            }
            if (c < 0x20 || c == 0x7F) {
                throw new IllegalArgumentException("Invalid character in display string");
            }
            out.append(c);
        }
        out.append('"');
    }

    private static void writeDecimal(BigDecimal value, StringBuilder out) {
        BigDecimal normalized = value.stripTrailingZeros();
        String text = normalized.toPlainString();
        int dot = text.indexOf('.');
        if (dot < 0) {
            out.append(text).append(".0");
            return;
        }
        int fracLen = text.length() - dot - 1;
        if (fracLen > 3) {
            throw new IllegalArgumentException("Decimal fraction must have at most 3 digits");
        }
        out.append(text);
    }

    private static boolean isTrueItem(JsonNode node) {
        if (!node.isArray() || node.size() != 2) {
            return false;
        }
        JsonNode bare = node.get(0);
        if (!bare.isBoolean() || !bare.booleanValue()) {
            return false;
        }
        return true;
    }

    private static void validateKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be empty");
        }
        char first = key.charAt(0);
        if (!isLowerAlpha(first)) {
            throw new IllegalArgumentException("Key must start with lowercase letter");
        }
        for (int i = 1; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!isKeyChar(c)) {
                throw new IllegalArgumentException("Invalid key character");
            }
        }
    }

    private static void validateToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token cannot be empty");
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!isTokenChar(c)) {
                throw new IllegalArgumentException("Invalid token character");
            }
        }
    }

    static String binaryToJsonValue(String base64Value) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid binary content");
        }
        return encodeBase32(bytes);
    }

    private static byte[] decodeBase32(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Binary value cannot be null");
        }
        String normalized = value.trim();
        int buffer = 0;
        int bitsLeft = 0;
        byte[] out = new byte[(normalized.length() * 5) / 8 + 1];
        int outPos = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '=') {
                break;
            }
            int val = base32Value(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid binary content");
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[outPos++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        byte[] result = new byte[outPos];
        System.arraycopy(out, 0, result, 0, outPos);
        return result;
    }

    private static String encodeBase32(byte[] data) {
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                sb.append(alphabet[index]);
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(alphabet[index]);
        }
        while (sb.length() % 8 != 0) {
            sb.append('=');
        }
        return sb.toString();
    }

    private static boolean isLowerAlpha(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isKeyChar(char c) {
        return isLowerAlpha(c) || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '*';
    }

    private static boolean isTokenChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '.' || c == '*' || c == '/';
    }

    private static int base32Value(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a';
        }
        if (c >= '2' && c <= '7') {
            return c - '2' + 26;
        }
        return -1;
    }
}
