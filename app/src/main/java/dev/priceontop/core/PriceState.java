package dev.priceontop.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PriceState {
    private final PriceQuote quote;
    private final long fetchedAtMillis;
    private final ProviderError error;

    private PriceState(PriceQuote quote, long fetchedAtMillis, ProviderError error) {
        this.quote = quote;
        this.fetchedAtMillis = fetchedAtMillis;
        this.error = error;
    }

    public static PriceState empty() {
        return new PriceState(null, 0L, null);
    }

    public static PriceState withQuote(PriceQuote quote, long fetchedAtMillis) {
        return new PriceState(quote, fetchedAtMillis, null);
    }

    public static PriceState withError(ProviderError error) {
        return new PriceState(null, 0L, error);
    }

    public boolean hasQuote() {
        return quote != null;
    }

    public PriceQuote quote() {
        return quote;
    }

    public long fetchedAtMillis() {
        return fetchedAtMillis;
    }

    public ProviderError error() {
        return error;
    }

    public boolean isStale(long nowMillis, RefreshPolicy refreshPolicy) {
        return !hasQuote() || nowMillis - fetchedAtMillis > refreshPolicy.staleThresholdMillis();
    }

    public boolean shouldHide(long nowMillis, RefreshPolicy refreshPolicy) {
        return !hasQuote() || nowMillis - fetchedAtMillis > refreshPolicy.hideThresholdMillis();
    }

    public Map<String, String> toSanitizedMap() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("hasQuote", Boolean.toString(hasQuote()));
        values.put("fetchedAtMillis", Long.toString(fetchedAtMillis));
        if (quote != null) {
            values.put("symbol", quote.symbol());
            values.put("price", Double.toString(quote.price()));
            values.put("currency", quote.currency());
            values.put("quoteTimestampMillis", Long.toString(quote.timestampMillis()));
            if (quote.hasPreviousClose()) {
                values.put("previousClose", Double.toString(quote.previousClose()));
            }
        }
        if (error != null) {
            values.put("errorCode", error.code().name());
            values.put("errorMessage", error.sanitizedMessageForIpc());
        }
        return values;
    }

    @Override
    public String toString() {
        return "PriceState" + DiagnosticsRedactor.redactMap(toSanitizedMap());
    }
}
