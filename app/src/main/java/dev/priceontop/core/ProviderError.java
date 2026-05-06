package dev.priceontop.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProviderError {
    public enum Code {
        NETWORK,
        TIMEOUT,
        INVALID_RESPONSE,
        RATE_LIMIT,
        UNAUTHORIZED,
        UNKNOWN
    }

    private final Code code;
    private final String message;

    private ProviderError(Code code, String message) {
        this.code = code == null ? Code.UNKNOWN : code;
        this.message = DiagnosticsRedactor.redact(message == null ? "" : message);
    }

    public static ProviderError of(Code code, String message) {
        return new ProviderError(code, message);
    }

    public Code code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String sanitizedMessageForIpc() {
        return "Provider error redacted";
    }

    public Map<String, String> toSanitizedMap() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("code", code.name());
        values.put("message", sanitizedMessageForIpc());
        return values;
    }

    @Override
    public String toString() {
        return "ProviderError" + DiagnosticsRedactor.redactMap(toSanitizedMap());
    }
}
