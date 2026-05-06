package dev.priceontop.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PriceConfig {
    private final boolean enabled;
    private final ProviderType providerType;
    private final List<String> symbols;
    private final String apiKey;
    private final RefreshPolicy refreshPolicy;
    private final String customUrlTemplate;
    private final String customJsonPathPrice;
    private final String customJsonPathSymbol;
    private final String customJsonPathCurrency;
    private final String customJsonPathTimestamp;

    private PriceConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.providerType = builder.providerType;
        this.symbols = normalizeSymbols(builder.symbols);
        this.apiKey = emptyToNull(builder.apiKey);
        this.refreshPolicy = RefreshPolicy.of(builder.refreshIntervalSeconds, builder.timeoutMillis);
        this.customUrlTemplate = emptyToNull(builder.customUrlTemplate);
        this.customJsonPathPrice = emptyToNull(builder.customJsonPathPrice);
        this.customJsonPathSymbol = emptyToNull(builder.customJsonPathSymbol);
        this.customJsonPathCurrency = emptyToNull(builder.customJsonPathCurrency);
        this.customJsonPathTimestamp = emptyToNull(builder.customJsonPathTimestamp);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean enabled() {
        return enabled;
    }

    public ProviderType providerType() {
        return providerType;
    }

    public List<String> symbols() {
        return symbols;
    }

    public String symbol() {
        return symbols.size() == 1 ? symbols.get(0) : null;
    }

    public String apiKey() {
        return apiKey;
    }

    public RefreshPolicy refreshPolicy() {
        return refreshPolicy;
    }

    public String customUrlTemplate() {
        return customUrlTemplate;
    }

    public String customJsonPathPrice() {
        return customJsonPathPrice;
    }

    public String customJsonPathSymbol() {
        return customJsonPathSymbol;
    }

    public String customJsonPathCurrency() {
        return customJsonPathCurrency;
    }

    public String customJsonPathTimestamp() {
        return customJsonPathTimestamp;
    }

    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        if (providerType == null) {
            errors.add("provider is required");
        } else if (providerType == ProviderType.CUSTOM_JSON) {
            if (customUrlTemplate == null || customUrlTemplate.isBlank()) {
                errors.add("custom URL template is required for CUSTOM_JSON");
            }
            if (customJsonPathPrice == null || customJsonPathPrice.isBlank()) {
                errors.add("price JSONPath is required for CUSTOM_JSON");
            }
        }
        if (symbols.size() != 1 || symbols.get(0).isBlank()) {
            errors.add("exactly one nonblank symbol is required");
        }
        errors.addAll(refreshPolicy.validationErrors());
        return ValidationResult.fromErrors(errors);
    }

    public Map<String, String> toSanitizedMap() {
        return toIpcMap(false);
    }

    public Map<String, String> toIpcMap(boolean includeSensitive) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.toString(enabled));
        values.put("providerType", providerType == null ? "" : providerType.name());
        values.put("symbol", symbol() == null ? "" : symbol());
        values.put("apiKey", apiKey == null ? "" : includeSensitive ? apiKey : "***");
        values.put("refreshIntervalSeconds", Integer.toString(refreshPolicy.refreshIntervalSeconds()));
        values.put("timeoutMillis", Integer.toString(refreshPolicy.timeoutMillis()));
        values.put("staleThresholdMillis", Long.toString(refreshPolicy.staleThresholdMillis()));
        values.put("hideThresholdMillis", Long.toString(refreshPolicy.hideThresholdMillis()));
        if (providerType == ProviderType.CUSTOM_JSON) {
            values.put("customUrlTemplate", customUrlTemplate == null ? "" : customUrlTemplate);
            values.put("customJsonPathPrice", customJsonPathPrice == null ? "" : customJsonPathPrice);
            values.put("customJsonPathSymbol", customJsonPathSymbol == null ? "" : customJsonPathSymbol);
            values.put("customJsonPathCurrency", customJsonPathCurrency == null ? "" : customJsonPathCurrency);
            values.put("customJsonPathTimestamp", customJsonPathTimestamp == null ? "" : customJsonPathTimestamp);
        }
        return Collections.unmodifiableMap(values);
    }

    @Override
    public String toString() {
        return "PriceConfig" + DiagnosticsRedactor.redactMap(toSanitizedMap());
    }

    private static List<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            normalized.add(symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return Collections.unmodifiableList(normalized);
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public static final class Builder {
        private boolean enabled;
        private ProviderType providerType;
        private List<String> symbols = List.of();
        private String apiKey;
        private Integer refreshIntervalSeconds;
        private Integer timeoutMillis;
        private String customUrlTemplate;
        private String customJsonPathPrice;
        private String customJsonPathSymbol;
        private String customJsonPathCurrency;
        private String customJsonPathTimestamp;

        private Builder() {
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder providerType(ProviderType providerType) {
            this.providerType = providerType;
            return this;
        }

        public Builder symbols(List<String> symbols) {
            this.symbols = symbols == null ? List.of() : new ArrayList<>(symbols);
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder refreshIntervalSeconds(Integer refreshIntervalSeconds) {
            this.refreshIntervalSeconds = refreshIntervalSeconds;
            return this;
        }

        public Builder timeoutMillis(Integer timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder customUrlTemplate(String customUrlTemplate) {
            this.customUrlTemplate = customUrlTemplate;
            return this;
        }

        public Builder customJsonPathPrice(String customJsonPathPrice) {
            this.customJsonPathPrice = customJsonPathPrice;
            return this;
        }

        public Builder customJsonPathSymbol(String customJsonPathSymbol) {
            this.customJsonPathSymbol = customJsonPathSymbol;
            return this;
        }

        public Builder customJsonPathCurrency(String customJsonPathCurrency) {
            this.customJsonPathCurrency = customJsonPathCurrency;
            return this;
        }

        public Builder customJsonPathTimestamp(String customJsonPathTimestamp) {
            this.customJsonPathTimestamp = customJsonPathTimestamp;
            return this;
        }

        public PriceConfig build() {
            return new PriceConfig(this);
        }
    }
}
