package dev.priceontop.format;

import static org.junit.Assert.assertEquals;

import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;
import dev.priceontop.core.RefreshPolicy;
import org.junit.Test;

public final class PriceFormatterTest {
    private static final long NOW_MILLIS = 1_720_000_000_000L;
    private final PriceFormatter formatter = new PriceFormatter();

    @Test
    public void formatsLargePrice() {
        assertEquals("BTC $65,000", formatter.format(freshQuote("BTC", 65_000.12d), NOW_MILLIS, RefreshPolicy.defaults()));
    }

    @Test
    public void formatsTwoDecimalsForOneToUnderOneHundred() {
        assertEquals("AAPL $12.30", formatter.format(freshQuote("AAPL", 12.3d), NOW_MILLIS, RefreshPolicy.defaults()));
    }

    @Test
    public void formatsSmallPriceWithTrimmedSixDecimalPlaces() {
        assertEquals("DOGE $0.1234", formatter.format(freshQuote("DOGE", 0.123400d), NOW_MILLIS, RefreshPolicy.defaults()));
        assertEquals("PEPE $0.000123", formatter.format(freshQuote("PEPE", 0.0001234d), NOW_MILLIS, RefreshPolicy.defaults()));
    }

    @Test
    public void truncatesLongSymbolsToTenDisplayCharacters() {
        assertEquals(
            "VERYLONGSY $123",
            formatter.format(freshQuote("VERYLONGSYMBOL", 123.0d), NOW_MILLIS, RefreshPolicy.defaults())
        );
    }

    @Test
    public void prefixesStaleMarkerBeforePrice() {
        PriceState stale = PriceState.withQuote(
            new PriceQuote("BTC", 65_000.12d, "$", NOW_MILLIS - 1_000L),
            NOW_MILLIS - RefreshPolicy.STALE_THRESHOLD_MILLIS - 1L
        );

        assertEquals("BTC ~$65,000", formatter.format(stale, NOW_MILLIS, RefreshPolicy.defaults()));
    }

    @Test
    public void returnsEmptyForNoQuoteErrorOrHiddenOldQuote() {
        PriceState hidden = PriceState.withQuote(
            new PriceQuote("BTC", 65_000.12d, "$", NOW_MILLIS - 1_000L),
            NOW_MILLIS - RefreshPolicy.HIDE_THRESHOLD_MILLIS - 1L
        );

        assertEquals("", formatter.format(PriceState.empty(), NOW_MILLIS, RefreshPolicy.defaults()));
        assertEquals(
            "",
            formatter.format(
                PriceState.withError(ProviderError.of(ProviderError.Code.INVALID_RESPONSE, "missing price")),
                NOW_MILLIS,
                RefreshPolicy.defaults()
            )
        );
        assertEquals("", formatter.format(hidden, NOW_MILLIS, RefreshPolicy.defaults()));
    }

    private static PriceState freshQuote(String symbol, double price) {
        return PriceState.withQuote(new PriceQuote(symbol, price, "$", NOW_MILLIS), NOW_MILLIS);
    }
}
