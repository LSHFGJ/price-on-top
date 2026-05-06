package dev.priceontop.core;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiagnosticsRedactor {
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
        "(?i)\\b(api[_-]?key|apikey|token|secret|authorization)\\b\\s*([=:])\\s*([^\\s,;&)]+)"
    );
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\bBearer\\s+[^\\s,;&)]+");

    private DiagnosticsRedactor() {
    }

    public static String redact(String value) {
        return redact(value, new String[0]);
    }

    public static String redact(String value, String... rawSecrets) {
        if (value == null) {
            return null;
        }

        String sanitized = value;
        if (rawSecrets != null) {
            for (String rawSecret : rawSecrets) {
                if (rawSecret != null && !rawSecret.isBlank()) {
                    sanitized = sanitized.replace(rawSecret, "***");
                }
            }
        }

        Matcher assignment = SENSITIVE_ASSIGNMENT.matcher(sanitized);
        StringBuffer buffer = new StringBuffer();
        while (assignment.find()) {
            assignment.appendReplacement(
                buffer,
                Matcher.quoteReplacement(assignment.group(1) + assignment.group(2) + "***")
            );
        }
        assignment.appendTail(buffer);
        return BEARER_TOKEN.matcher(buffer.toString()).replaceAll("Bearer ***");
    }

    public static String redactMap(Map<String, ?> values) {
        if (values == null) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey()).append('=');
            if (isSensitiveKey(entry.getKey())) {
                builder.append("***");
            } else {
                builder.append(redact(String.valueOf(entry.getValue())));
            }
        }
        return builder.append('}').toString();
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("apikey")
            || normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("authorization");
    }
}
