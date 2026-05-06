package dev.priceontop.provider;

import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderType;
import dev.priceontop.core.RefreshPolicy;
import java.util.Locale;

public interface PriceProvider {
    ProviderType type();

    PriceState fetch(Request request);

    final class Request {
        private final String symbol;
        private final String apiKey;
        private final RefreshPolicy refreshPolicy;
        private final long nowMillis;
        private final String displayCurrency;

        private Request(Builder builder) {
            this.symbol = builder.symbol == null ? "" : builder.symbol.trim().toUpperCase(Locale.ROOT);
            this.apiKey = builder.apiKey == null ? "" : builder.apiKey;
            this.refreshPolicy = builder.refreshPolicy == null ? RefreshPolicy.defaults() : builder.refreshPolicy;
            this.nowMillis = builder.nowMillis == null ? System.currentTimeMillis() : builder.nowMillis;
            this.displayCurrency = builder.displayCurrency == null ? "" : builder.displayCurrency.trim();
        }

        public static Builder builder() {
            return new Builder();
        }

        public String symbol() {
            return symbol;
        }

        public String apiKey() {
            return apiKey;
        }

        public RefreshPolicy refreshPolicy() {
            return refreshPolicy;
        }

        public long nowMillis() {
            return nowMillis;
        }

        public String displayCurrencyOr(String fallback) {
            return displayCurrency.isBlank() ? fallback : displayCurrency;
        }

        public static final class Builder {
            private String symbol;
            private String apiKey;
            private RefreshPolicy refreshPolicy;
            private Long nowMillis;
            private String displayCurrency;

            private Builder() {
            }

            public Builder symbol(String symbol) {
                this.symbol = symbol;
                return this;
            }

            public Builder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public Builder refreshPolicy(RefreshPolicy refreshPolicy) {
                this.refreshPolicy = refreshPolicy;
                return this;
            }

            public Builder nowMillis(long nowMillis) {
                this.nowMillis = nowMillis;
                return this;
            }

            public Builder displayCurrency(String displayCurrency) {
                this.displayCurrency = displayCurrency;
                return this;
            }

            public Request build() {
                return new Request(this);
            }
        }
    }
}
