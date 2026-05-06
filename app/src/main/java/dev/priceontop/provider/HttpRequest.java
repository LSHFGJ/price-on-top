package dev.priceontop.provider;

import dev.priceontop.core.DiagnosticsRedactor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HttpRequest {
    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final List<String> sensitiveValues;

    private HttpRequest(String method, String url, Map<String, String> headers, List<String> sensitiveValues) {
        this.method = method;
        this.url = url == null ? "" : url;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers == null ? Map.of() : headers));
        this.sensitiveValues = List.copyOf(sensitiveValues == null ? List.of() : sensitiveValues);
    }

    public static HttpRequest get(String url, Map<String, String> headers, String... sensitiveValues) {
        return new HttpRequest("GET", url, headers, sensitiveValues == null ? List.of() : List.of(sensitiveValues));
    }

    public String method() {
        return method;
    }

    public String url() {
        return url;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String toSanitizedString() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("method", method);
        values.put("url", sanitize(url));
        values.put("headers", sanitize(DiagnosticsRedactor.redactMap(headers)));
        return "HttpRequest" + DiagnosticsRedactor.redactMap(values);
    }

    private String sanitize(String value) {
        return DiagnosticsRedactor.redact(value, sensitiveValues.toArray(new String[0]));
    }
}
