package dev.priceontop.core;

public final class PriceQuote {
    private final String symbol;
    private final double price;
    private final String currency;
    private final long timestampMillis;
    private final Double previousClose;

    public PriceQuote(String symbol, double price, String currency, long timestampMillis) {
        this(symbol, price, currency, timestampMillis, null);
    }

    public PriceQuote(String symbol, double price, String currency, long timestampMillis, Double previousClose) {
        this.symbol = symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT);
        this.price = price;
        this.currency = currency == null ? "" : currency.trim().toUpperCase(java.util.Locale.ROOT);
        this.timestampMillis = timestampMillis;
        this.previousClose = previousClose;
    }

    public String symbol() {
        return symbol;
    }

    public double price() {
        return price;
    }

    public String currency() {
        return currency;
    }

    public long timestampMillis() {
        return timestampMillis;
    }

    public boolean hasPreviousClose() {
        return previousClose != null;
    }

    public double previousClose() {
        return previousClose == null ? Double.NaN : previousClose;
    }

    @Override
    public String toString() {
        return "PriceQuote{symbol='" + symbol + '\''
            + ", price=" + price
            + ", currency='" + currency + '\''
            + ", timestampMillis=" + timestampMillis
            + ", previousClose=" + previousClose
            + '}';
    }
}
