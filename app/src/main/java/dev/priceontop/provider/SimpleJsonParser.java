package dev.priceontop.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleJsonParser {
    private final String input;
    private int index;

    private SimpleJsonParser(String input) {
        this.input = input == null ? "" : input;
    }

    static Object parse(String input) {
        SimpleJsonParser parser = new SimpleJsonParser(input);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("unexpected trailing content");
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (isAtEnd()) {
            throw error("unexpected end of input");
        }
        char value = input.charAt(index);
        if (value == '{') {
            return parseObject();
        }
        if (value == '[') {
            return parseArray();
        }
        if (value == '"') {
            return parseString();
        }
        if (value == 't') {
            expectLiteral("true");
            return Boolean.TRUE;
        }
        if (value == 'f') {
            expectLiteral("false");
            return Boolean.FALSE;
        }
        if (value == 'n') {
            expectLiteral("null");
            return null;
        }
        if (value == '-' || Character.isDigit(value)) {
            return parseNumber();
        }
        throw error("unexpected token '" + value + "'");
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> values = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) {
            index++;
            return values;
        }
        while (true) {
            skipWhitespace();
            if (!peek('"')) {
                throw error("object key must be a string");
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            values.put(key, parseValue());
            skipWhitespace();
            if (peek('}')) {
                index++;
                return values;
            }
            expect(',');
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> values = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            index++;
            return values;
        }
        while (true) {
            values.add(parseValue());
            skipWhitespace();
            if (peek(']')) {
                index++;
                return values;
            }
            expect(',');
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (!isAtEnd()) {
            char value = input.charAt(index++);
            if (value == '"') {
                return builder.toString();
            }
            if (value == '\\') {
                if (isAtEnd()) {
                    throw error("unterminated escape sequence");
                }
                char escape = input.charAt(index++);
                switch (escape) {
                    case '"':
                    case '\\':
                    case '/':
                        builder.append(escape);
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        builder.append(parseUnicodeEscape());
                        break;
                    default:
                        throw error("unsupported escape sequence");
                }
            } else {
                builder.append(value);
            }
        }
        throw error("unterminated string");
    }

    private char parseUnicodeEscape() {
        if (index + 4 > input.length()) {
            throw error("incomplete unicode escape");
        }
        String hex = input.substring(index, index + 4);
        index += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException exception) {
            throw error("invalid unicode escape");
        }
    }

    private Number parseNumber() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        consumeDigits();
        if (peek('.')) {
            index++;
            consumeDigits();
        }
        if (peek('e') || peek('E')) {
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            consumeDigits();
        }
        try {
            return Double.valueOf(input.substring(start, index));
        } catch (NumberFormatException exception) {
            throw error("invalid number");
        }
    }

    private void consumeDigits() {
        int start = index;
        while (!isAtEnd() && Character.isDigit(input.charAt(index))) {
            index++;
        }
        if (start == index) {
            throw error("expected digit");
        }
    }

    private void expectLiteral(String literal) {
        if (!input.startsWith(literal, index)) {
            throw error("expected " + literal);
        }
        index += literal.length();
    }

    private void expect(char expected) {
        skipWhitespace();
        if (isAtEnd() || input.charAt(index) != expected) {
            throw error("expected '" + expected + "'");
        }
        index++;
    }

    private boolean peek(char expected) {
        return !isAtEnd() && input.charAt(index) == expected;
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private boolean isAtEnd() {
        return index >= input.length();
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("malformed JSON: " + message + " at offset " + index);
    }
}
