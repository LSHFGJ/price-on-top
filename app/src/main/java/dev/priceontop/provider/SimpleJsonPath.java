package dev.priceontop.provider;

import java.util.List;
import java.util.Map;

final class SimpleJsonPath {
    private SimpleJsonPath() {
    }

    static Result read(Object root, String path) {
        if (path == null || path.isBlank()) {
            return Result.missing();
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("$")) {
            throw new IllegalArgumentException("unsupported JSONPath: path must start with $");
        }

        Object current = root;
        int index = 1;
        while (index < trimmed.length()) {
            char token = trimmed.charAt(index);
            if (token == '.') {
                int start = ++index;
                while (index < trimmed.length() && trimmed.charAt(index) != '.' && trimmed.charAt(index) != '[') {
                    index++;
                }
                if (start == index) {
                    throw new IllegalArgumentException("unsupported JSONPath: empty property segment");
                }
                String name = trimmed.substring(start, index);
                if (!(current instanceof Map<?, ?> map) || !map.containsKey(name)) {
                    return Result.missing();
                }
                current = map.get(name);
            } else if (token == '[') {
                int close = trimmed.indexOf(']', index);
                if (close < 0) {
                    throw new IllegalArgumentException("unsupported JSONPath: missing ]");
                }
                String value = trimmed.substring(index + 1, close).trim();
                int arrayIndex;
                try {
                    arrayIndex = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("unsupported JSONPath: only numeric array indexes are supported");
                }
                if (!(current instanceof List<?> list) || arrayIndex < 0 || arrayIndex >= list.size()) {
                    return Result.missing();
                }
                current = list.get(arrayIndex);
                index = close + 1;
            } else {
                throw new IllegalArgumentException("unsupported JSONPath: expected . or [");
            }
        }
        return Result.found(current);
    }

    static final class Result {
        private final boolean found;
        private final Object value;

        private Result(boolean found, Object value) {
            this.found = found;
            this.value = value;
        }

        static Result found(Object value) {
            return new Result(true, value);
        }

        static Result missing() {
            return new Result(false, null);
        }

        boolean found() {
            return found;
        }

        Object value() {
            return value;
        }
    }
}
