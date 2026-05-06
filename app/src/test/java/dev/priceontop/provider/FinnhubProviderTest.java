package dev.priceontop.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;
import dev.priceontop.core.RefreshPolicy;
import java.io.IOException;
import org.junit.Test;

public final class FinnhubProviderTest {
    private static final String RAW_API_KEY = "test-secret-finnhub-key";
    private static final long FETCHED_AT_MILLIS = 1_720_000_000_000L;

    @Test
    public void parseQuoteSuccess() {
        RecordingTransport transport = RecordingTransport.respondingWith(
            new HttpResponse(200, "{ \"c\": 123.45, \"pc\": 120.00, \"t\": 1710000000 }")
        );
        FinnhubProvider provider = new FinnhubProvider(transport);

        PriceState state = provider.fetch(defaultRequest("BTC"));

        assertTrue(state.hasQuote());
        assertEquals(FETCHED_AT_MILLIS, state.fetchedAtMillis());
        PriceQuote quote = state.quote();
        assertEquals("BTC", quote.symbol());
        assertEquals(123.45, quote.price(), 0.000001d);
        assertEquals(120.00, quote.previousClose(), 0.000001d);
        assertEquals(1_710_000_000_000L, quote.timestampMillis());
        assertEquals("$", quote.currency());
        assertEquals("GET", transport.lastRequest.method());
        assertEquals(
            "https://finnhub.io/api/v1/quote?symbol=BTC&token=" + RAW_API_KEY,
            transport.lastRequest.url()
        );
        assertEquals(RefreshPolicy.DEFAULT_TIMEOUT_MILLIS, transport.lastTimeoutMillis);
        assertFalse(transport.lastRequest.toSanitizedString().contains(RAW_API_KEY));
    }

    @Test
    public void http429ReturnsRateLimitError() {
        RecordingTransport transport = RecordingTransport.respondingWith(
            new HttpResponse(429, "{\"error\":\"token=" + RAW_API_KEY + " exceeded\"}")
        );

        PriceState state = new FinnhubProvider(transport).fetch(defaultRequest("BTC"));

        assertFalse(state.hasQuote());
        assertNotNull(state.error());
        assertEquals(ProviderError.Code.RATE_LIMIT, state.error().code());
        assertFalse(state.error().message().contains(RAW_API_KEY));
        assertTrue(state.error().message().contains("rate"));
    }

    @Test
    public void encodesSymbolAndApiKeyInQuoteUrl() {
        RecordingTransport transport = RecordingTransport.respondingWith(
            new HttpResponse(200, "{ \"c\": 1.23, \"pc\": 1.20, \"t\": 1710000000 }")
        );
        PriceProvider.Request request = PriceProvider.Request.builder()
            .symbol("BRK B")
            .apiKey("key+/ =")
            .refreshPolicy(RefreshPolicy.defaults())
            .nowMillis(FETCHED_AT_MILLIS)
            .build();

        new FinnhubProvider(transport).fetch(request);

        assertEquals(
            "https://finnhub.io/api/v1/quote?symbol=BRK+B&token=key%2B%2F+%3D",
            transport.lastRequest.url()
        );
    }

    @Test
    public void timeoutReturnsTimeoutProviderError() {
        RecordingTransport transport = RecordingTransport.throwing(new HttpTransport.TimeoutException("socket timeout"));

        PriceState state = new FinnhubProvider(transport).fetch(defaultRequest("AAPL"));

        assertFalse(state.hasQuote());
        assertNotNull(state.error());
        assertEquals(ProviderError.Code.TIMEOUT, state.error().code());
    }

    @Test
    public void unauthorizedStatusReturnsSanitizedProviderError() {
        RecordingTransport transport = RecordingTransport.respondingWith(
            new HttpResponse(401, "{\"error\":\"invalid token=" + RAW_API_KEY + "\"}")
        );

        PriceState state = new FinnhubProvider(transport).fetch(defaultRequest("AAPL"));

        assertFalse(state.hasQuote());
        assertNotNull(state.error());
        assertEquals(ProviderError.Code.UNAUTHORIZED, state.error().code());
        assertFalse(state.error().message().contains(RAW_API_KEY));
        assertTrue(state.error().message().contains("unauthorized"));
    }

    private static PriceProvider.Request defaultRequest(String symbol) {
        return PriceProvider.Request.builder()
            .symbol(symbol)
            .apiKey(RAW_API_KEY)
            .refreshPolicy(RefreshPolicy.defaults())
            .nowMillis(FETCHED_AT_MILLIS)
            .build();
    }

    private static final class RecordingTransport implements HttpTransport {
        private final HttpResponse response;
        private final IOException failure;
        private HttpRequest lastRequest;
        private int lastTimeoutMillis;

        private RecordingTransport(HttpResponse response, IOException failure) {
            this.response = response;
            this.failure = failure;
        }

        static RecordingTransport respondingWith(HttpResponse response) {
            return new RecordingTransport(response, null);
        }

        static RecordingTransport throwing(IOException failure) {
            return new RecordingTransport(null, failure);
        }

        @Override
        public HttpResponse get(HttpRequest request, int timeoutMillis) throws IOException {
            this.lastRequest = request;
            this.lastTimeoutMillis = timeoutMillis;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}
