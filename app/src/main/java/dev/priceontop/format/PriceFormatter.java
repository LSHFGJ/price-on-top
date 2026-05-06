package dev.priceontop.format;

import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.RefreshPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class PriceFormatter {
    private static final int MAX_SYMBOL_DISPLAY_CHARS = 10;
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);

    public String format(PriceState state, long nowMillis, RefreshPolicy refreshPolicy) {
        if (state == null || !state.hasQuote()) {
            return "";
        }
        RefreshPolicy policy = refreshPolicy == null ? RefreshPolicy.defaults() : refreshPolicy;
        if (state.shouldHide(nowMillis, policy)) {
            return "";
        }
        PriceQuote quote = state.quote();
        String stalePrefix = state.isStale(nowMillis, policy) ? "~" : "";
        return truncateSymbol(quote.symbol()) + " " + stalePrefix + formatPrice(quote);
    }

    private static String formatPrice(PriceQuote quote) {
        String amount = formatAmount(quote.price());
        String currency = currencyPrefix(quote.currency());
        return currency + amount;
    }

    private static String formatAmount(double price) {
        double absolute = Math.abs(price);
        if (absolute >= 100d) {
            return decimalFormat("#,##0").format(price);
        }
        if (absolute >= 1d) {
            return decimalFormat("#,##0.00").format(price);
        }
        BigDecimal rounded = BigDecimal.valueOf(price).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return rounded.toPlainString();
    }

    private static DecimalFormat decimalFormat(String pattern) {
        DecimalFormat format = new DecimalFormat(pattern, SYMBOLS);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    private static String currencyPrefix(String currency) {
        if (currency == null || currency.isBlank()) {
            return "";
        }
        return switch (currency.trim().toUpperCase(Locale.ROOT)) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "JPY" -> "¥";
            default -> currency.trim().length() == 1 ? currency.trim() : currency.trim() + " ";
        };
    }

    private static String truncateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        int count = symbol.codePointCount(0, symbol.length());
        if (count <= MAX_SYMBOL_DISPLAY_CHARS) {
            return symbol;
        }
        return symbol.substring(0, symbol.offsetByCodePoints(0, MAX_SYMBOL_DISPLAY_CHARS));
    }
}
