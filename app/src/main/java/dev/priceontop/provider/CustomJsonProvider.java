package dev.priceontop.provider;

import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;
import dev.priceontop.core.ProviderType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CustomJsonProvider implements PriceProvider {
    private final Configuration configuration;
    private final HttpTransport transport;

    public CustomJsonProvider(Configuration configuration, HttpTransport transport) {
        this.configuration = configuration;
        this.transport = transport;
    }

    @Override
    public ProviderType type() {
        return ProviderType.CUSTOM_JSON;
    }

    @Override
    public PriceState fetch(Request request) {
        HttpRequest httpRequest = HttpRequest.get(buildUrl(request), configuration.headers(), request.apiKey());
        try {
            HttpResponse response = transport.get(httpRequest, request.refreshPolicy().timeoutMillis());
            if (response.statusCode() != 200) {
                return ProviderDiagnostics.error(
                    ProviderDiagnostics.codeForHttpStatus(response.statusCode()),
                    ProviderDiagnostics.messageForHttpStatus("Custom JSON", response, httpRequest),
                    request.apiKey()
                );
            }
            return parseQuote(response.body(), request);
        } catch (HttpTransport.TimeoutException exception) {
            return ProviderDiagnostics.error(ProviderError.Code.TIMEOUT, "Custom JSON timeout: " + exception.getMessage(), request.apiKey());
        } catch (IOException exception) {
            return ProviderDiagnostics.error(ProviderError.Code.NETWORK, "Custom JSON network error: " + exception.getMessage(), request.apiKey());
        } catch (IllegalArgumentException exception) {
            return ProviderDiagnostics.error(ProviderError.Code.INVALID_RESPONSE, exception.getMessage(), request.apiKey());
        }
    }

    private PriceState parseQuote(String body, Request request) {
        Object json = SimpleJsonParser.parse(body);
        double price = FinnhubProvider.requiredNumber(json, configuration.pricePath(), "price");
        String symbol = optionalString(json, configuration.symbolPath(), request.symbol());
        String currency = optionalString(json, configuration.currencyPath(), request.displayCurrencyOr(""));
        Long timestampMillis = configuration.timestampPath().isBlank()
            ? null
            : FinnhubProvider.optionalTimestampMillis(json, configuration.timestampPath());
        PriceQuote quote = new PriceQuote(
            symbol,
            price,
            currency,
            timestampMillis == null ? request.nowMillis() : timestampMillis
        );
        return PriceState.withQuote(quote, request.nowMillis());
    }

    private String buildUrl(Request request) {
        return configuration.urlTemplate()
            .replace("{symbol}", encode(request.symbol()))
            .replace("{apiKey}", encode(request.apiKey()));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 encoding is unavailable", exception);
        }
    }

    private static String optionalString(Object json, String path, String fallback) {
        if (path == null || path.isBlank()) {
            return fallback;
        }
        SimpleJsonPath.Result result = SimpleJsonPath.read(json, path);
        if (!result.found() || result.value() == null) {
            return fallback;
        }
        return String.valueOf(result.value());
    }

    public static final class Configuration {
        private final String urlTemplate;
        private final Map<String, String> headers;
        private final String pricePath;
        private final String symbolPath;
        private final String currencyPath;
        private final String timestampPath;

        private Configuration(Builder builder) {
            this.urlTemplate = builder.urlTemplate == null ? "" : builder.urlTemplate.trim();
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
            this.pricePath = builder.pricePath == null ? "" : builder.pricePath.trim();
            this.symbolPath = builder.symbolPath == null ? "" : builder.symbolPath.trim();
            this.currencyPath = builder.currencyPath == null ? "" : builder.currencyPath.trim();
            this.timestampPath = builder.timestampPath == null ? "" : builder.timestampPath.trim();
        }

        public static Builder builder() {
            return new Builder();
        }

        public String urlTemplate() {
            return urlTemplate;
        }

        public Map<String, String> headers() {
            return headers;
        }

        public String pricePath() {
            return pricePath;
        }

        public String symbolPath() {
            return symbolPath;
        }

        public String currencyPath() {
            return currencyPath;
        }

        public String timestampPath() {
            return timestampPath;
        }

        public static final class Builder {
            private String urlTemplate;
            private final Map<String, String> headers = new LinkedHashMap<>();
            private String pricePath;
            private String symbolPath;
            private String currencyPath;
            private String timestampPath;

            private Builder() {
            }

            public Builder urlTemplate(String urlTemplate) {
                this.urlTemplate = urlTemplate;
                return this;
            }

            public Builder header(String name, String value) {
                if (name != null && !name.isBlank()) {
                    headers.put(name, value == null ? "" : value);
                }
                return this;
            }

            public Builder pricePath(String pricePath) {
                this.pricePath = pricePath;
                return this;
            }

            public Builder symbolPath(String symbolPath) {
                this.symbolPath = symbolPath;
                return this;
            }

            public Builder currencyPath(String currencyPath) {
                this.currencyPath = currencyPath;
                return this;
            }

            public Builder timestampPath(String timestampPath) {
                this.timestampPath = timestampPath;
                return this;
            }

            public Configuration build() {
                return new Configuration(this);
            }
        }
    }
}
