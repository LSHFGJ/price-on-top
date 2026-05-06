package dev.priceontop.settings;

import java.util.ArrayList;
import java.util.List;

import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.ProviderType;
import dev.priceontop.core.ValidationResult;
import dev.priceontop.storage.PriceStorage;
import dev.priceontop.provider.PriceProvider;
import dev.priceontop.provider.FinnhubProvider;
import dev.priceontop.provider.CustomJsonProvider;
import dev.priceontop.provider.HttpTransport;
import dev.priceontop.provider.HttpRequest;
import dev.priceontop.provider.HttpResponse;
import dev.priceontop.core.PriceState;
import dev.priceontop.format.PriceFormatter;
import java.io.IOException;

public class SettingsViewModel {
    private final PriceStorage storage;
    
    private boolean enabled;
    private String provider = "FINNHUB";
    private String symbol = "";
    private String apiKey = "";
    private String customUrlTemplate = "";
    private String customJsonPathPrice = "";
    private String customJsonPathSymbol = "";
    private String customJsonPathCurrency = "";
    private String customJsonPathTimestamp = "";
    private String refreshInterval = "120";
    private String timeoutMillis = "3000";
    private String lastSuccessfulPreview = "";

    private final List<String> errors = new ArrayList<>();

    public SettingsViewModel(PriceStorage storage) {
        this.storage = storage;
        loadFromStorage();
    }

    private void loadFromStorage() {
        PriceConfig config = storage.loadConfig();
        if (config != null) {
            enabled = config.enabled();
            provider = config.providerType() != null ? config.providerType().name() : "FINNHUB";
            symbol = config.symbol() != null ? config.symbol() : "";
            apiKey = config.apiKey() != null ? config.apiKey() : "";
            refreshInterval = String.valueOf(config.refreshPolicy().refreshIntervalSeconds());
            timeoutMillis = String.valueOf(config.refreshPolicy().timeoutMillis());
            if (config.customUrlTemplate() != null) customUrlTemplate = config.customUrlTemplate();
            if (config.customJsonPathPrice() != null) customJsonPathPrice = config.customJsonPathPrice();
            if (config.customJsonPathSymbol() != null) customJsonPathSymbol = config.customJsonPathSymbol();
            if (config.customJsonPathCurrency() != null) customJsonPathCurrency = config.customJsonPathCurrency();
            if (config.customJsonPathTimestamp() != null) customJsonPathTimestamp = config.customJsonPathTimestamp();
        }
    }

    public boolean save() {
        errors.clear();
        
        ProviderType providerType = null;
        try {
            providerType = ProviderType.valueOf(provider);
        } catch (IllegalArgumentException e) {
            errors.add("Invalid provider");
        }

        int parsedRefreshInterval = 120;
        try {
            parsedRefreshInterval = Integer.parseInt(refreshInterval);
            if (parsedRefreshInterval <= 0) {
                errors.add("refresh interval must be positive");
            }
        } catch (NumberFormatException e) {
            errors.add("refresh interval must be a number");
        }

        int parsedTimeout = 3000;
        try {
            parsedTimeout = Integer.parseInt(timeoutMillis);
        } catch (NumberFormatException e) {
            errors.add("timeout must be a number");
        }

        if (ProviderType.CUSTOM_JSON.name().equals(provider)) {
            if (customJsonPathPrice == null || customJsonPathPrice.isBlank()) {
                errors.add("Price JSONPath is required for custom provider");
            }
        }

        PriceConfig.Builder builder = PriceConfig.builder()
            .enabled(enabled)
            .providerType(providerType)
            .apiKey(apiKey)
            .refreshIntervalSeconds(parsedRefreshInterval)
            .timeoutMillis(parsedTimeout)
            .customUrlTemplate(customUrlTemplate)
            .customJsonPathPrice(customJsonPathPrice)
            .customJsonPathSymbol(customJsonPathSymbol)
            .customJsonPathCurrency(customJsonPathCurrency)
            .customJsonPathTimestamp(customJsonPathTimestamp);
            
        if (symbol != null && !symbol.isBlank()) {
            builder.symbols(List.of(symbol));
        }

        PriceConfig config = builder.build();
        ValidationResult result = config.validate();
        
        errors.addAll(result.errors());

        if (errors.isEmpty()) {
            storage.saveConfig(config);
            return true;
        }
        return false;
    }

    public String testProvider() {
        ProviderType providerType = null;
        try {
            providerType = ProviderType.valueOf(provider);
        } catch (IllegalArgumentException ignored) {}

        int parsedRefreshInterval = 120;
        try {
            parsedRefreshInterval = Integer.parseInt(refreshInterval);
        } catch (NumberFormatException ignored) {}

        int parsedTimeout = 3000;
        try {
            parsedTimeout = Integer.parseInt(timeoutMillis);
        } catch (NumberFormatException ignored) {}

        PriceConfig.Builder builder = PriceConfig.builder()
            .enabled(enabled)
            .providerType(providerType)
            .apiKey(apiKey)
            .refreshIntervalSeconds(parsedRefreshInterval)
            .timeoutMillis(parsedTimeout)
            .customUrlTemplate(customUrlTemplate)
            .customJsonPathPrice(customJsonPathPrice)
            .customJsonPathSymbol(customJsonPathSymbol)
            .customJsonPathCurrency(customJsonPathCurrency)
            .customJsonPathTimestamp(customJsonPathTimestamp);
            
        if (symbol != null && !symbol.isBlank()) {
            builder.symbols(List.of(symbol));
        }
        
        PriceConfig config = builder.build();
        ValidationResult result = config.validate();
        
        if (!result.isValid()) {
            return "Configuration invalid:\n- " + String.join("\n- ", result.errors());
        }
        
        HttpTransport fakeTransport = new HttpTransport() {
            @Override
            public HttpResponse get(HttpRequest req, int timeout) throws IOException {
                if (config.providerType() == ProviderType.FINNHUB) {
                    return new HttpResponse(200, "{\"c\": 123.45, \"pc\": 120.0, \"t\": 1600000000}");
                } else {
                    return new HttpResponse(200, "{\"data\": {\"quote\": {\"price\": 123.45, \"symbol\": \"MOCK\", \"currency\": \"$\", \"timestamp\": 1600000000}}, \"price\": 123.45, \"symbol\": \"MOCK\", \"currency\": \"$\", \"timestamp\": 1600000000}");
                }
            }
        };
        
        PriceProvider provider;
        if (config.providerType() == ProviderType.FINNHUB) {
            provider = new FinnhubProvider(fakeTransport);
        } else {
            CustomJsonProvider.Configuration.Builder conf = CustomJsonProvider.Configuration.builder()
                .urlTemplate(config.customUrlTemplate())
                .pricePath(config.customJsonPathPrice())
                .symbolPath(config.customJsonPathSymbol())
                .currencyPath(config.customJsonPathCurrency())
                .timestampPath(config.customJsonPathTimestamp());
            provider = new CustomJsonProvider(conf.build(), fakeTransport);
        }
        
        PriceProvider.Request req = PriceProvider.Request.builder()
            .symbol(config.symbol())
            .apiKey(config.apiKey())
            .displayCurrency("$")
            .refreshPolicy(config.refreshPolicy())
            .nowMillis(1600000000000L)
            .build();
            
        PriceState state = provider.fetch(req);
        
        if (state.error() != null) {
            return "Provider Error:\n" + state.error().code() + ": " + state.error().message();
        }
        
        String preview = new PriceFormatter().format(state, 1600000000000L, config.refreshPolicy());
        String fullPreview = "Preview: " + preview + "\nProvider: " + config.providerType() + "\nSymbol: " + config.symbol() + "\nAPI Key: " + (config.apiKey() != null && !config.apiKey().isEmpty() ? "***" : "none");
        lastSuccessfulPreview = fullPreview;
        return fullPreview;
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setApiKey(String apiKey) {
        if (apiKey != null && apiKey.equals("***")) {
            return;
        }
        this.apiKey = apiKey;
    }
    public void setRefreshInterval(String refreshInterval) { this.refreshInterval = refreshInterval; }
    public void setCustomUrlTemplate(String customUrlTemplate) { this.customUrlTemplate = customUrlTemplate; }
    public void setCustomJsonPathPrice(String customJsonPathPrice) { this.customJsonPathPrice = customJsonPathPrice; }

    public boolean isEnabled() { return enabled; }
    public String getProvider() { return provider; }
    public String getSymbol() { return symbol; }
    public String getApiKey() { return apiKey; }
    
    public String getDisplayApiKey() {
        return (apiKey == null || apiKey.isEmpty()) ? "" : "***";
    }
    
    public String getLastSuccessfulPreview() {
        return lastSuccessfulPreview;
    }
    public String getRefreshInterval() { return refreshInterval; }
    public String getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(String timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    public String getCustomUrlTemplate() { return customUrlTemplate; }
    public String getCustomJsonPathPrice() { return customJsonPathPrice; }
    public String getCustomJsonPathSymbol() { return customJsonPathSymbol; }
    public void setCustomJsonPathSymbol(String customJsonPathSymbol) { this.customJsonPathSymbol = customJsonPathSymbol; }
    public String getCustomJsonPathCurrency() { return customJsonPathCurrency; }
    public void setCustomJsonPathCurrency(String customJsonPathCurrency) { this.customJsonPathCurrency = customJsonPathCurrency; }
    public String getCustomJsonPathTimestamp() { return customJsonPathTimestamp; }
    public void setCustomJsonPathTimestamp(String customJsonPathTimestamp) { this.customJsonPathTimestamp = customJsonPathTimestamp; }
}
