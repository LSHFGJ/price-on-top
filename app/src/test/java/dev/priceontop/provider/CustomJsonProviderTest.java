package dev.priceontop.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;
import dev.priceontop.core.RefreshPolicy;
import dev.priceontop.format.PriceFormatter;
import java.io.IOException;
import org.junit.Test;

public final class CustomJsonProviderTest {
    private static final String RAW_API_KEY = "test-secret-custom-key";
    private static final long FETCHED_AT_MILLIS = 1_720_000_000_000L;

    @Test
    public void extractsNestedJsonPathQuoteSuccess() {
        RecordingTransport transport = RecordingTransport.respondingWith(new HttpResponse(
            200,
            "{\"data\":{\"quote\":{\"price\":0.123400,\"symbol\":\"ethusd\",\"currency\":\"$\",\"timestamp\":1710000123}}}"
        ));
        CustomJsonProvider provider = new CustomJsonProvider(defaultConfiguration(), transport);

        PriceState state = provider.fetch(defaultRequest("ETHUSD"));

        assertTrue(state.hasQuote());
        PriceQuote quote = state.quote();
        assertEquals("ETHUSD", quote.symbol());
        assertEquals(0.1234d, quote.price(), 0.0000001d);
        assertEquals("$", quote.currency());
        assertEquals(1_710_000_123_000L, quote.timestampMillis());
        assertEquals("GET", transport.lastRequest.method());
        assertEquals("application/json", transport.lastRequest.headers().get("Accept"));
        assertEquals(
            "https://example.test/quotes/ETHUSD?apiKey=" + RAW_API_KEY,
            transport.lastRequest.url()
        );
        assertFalse(transport.lastRequest.toSanitizedString().contains(RAW_API_KEY));
    }

    @Test
    public void missingPriceReturnsProviderError() {
        RecordingTransport transport = RecordingTransport.respondingWith(
            new HttpResponse(200, "{\"data\":{\"quote\":{\"symbol\":\"BTC\"}}}")
        );

        PriceState state = new CustomJsonProvider(defaultConfiguration(), transport).fetch(defaultRequest("BTC"));

        assertFalse(state.hasQuote());
        assertNotNull(state.error());
        assertEquals(ProviderError.Code.INVALID_RESPONSE, state.error().code());
        assertTrue(state.error().message().contains("price"));
        assertFalse(state.error().message().contains(RAW_API_KEY));
        assertEquals("", new PriceFormatter().format(state, FETCHED_AT_MILLIS, RefreshPolicy.defaults()));
    }

    @Test
    public void encodesTemplatePlaceholdersInCustomUrl() {
        RecordingTransport transport = RecordingTransport.respondingWith(
            new HttpResponse(200, "{\"data\":{\"quote\":{\"price\":2.50}}}")
        );
        PriceProvider.Request request = PriceProvider.Request.builder()
            .symbol("BRK B")
            .apiKey("key+/ =")
            .refreshPolicy(RefreshPolicy.defaults())
            .nowMillis(FETCHED_AT_MILLIS)
            .build();

        new CustomJsonProvider(defaultConfiguration(), transport).fetch(request);

        assertEquals(
            "https://example.test/quotes/BRK+B?apiKey=key%2B%2F+%3D",
            transport.lastRequest.url()
        );
    }

    @Test
    public void malformedJsonReturnsProviderError() {
        RecordingTransport transport = RecordingTransport.respondingWith(new HttpResponse(200, "{\"data\": "));

        PriceState state = new CustomJsonProvider(defaultConfiguration(), transport).fetch(defaultRequest("BTC"));

        assertFalse(state.hasQuote());
        assertNotNull(state.error());
        assertEquals(ProviderError.Code.INVALID_RESPONSE, state.error().code());
        assertTrue(state.error().message().contains("malformed"));
    }

    @Test
    public void nullPriceReturnsProviderError() {
        RecordingTransport transport = RecordingTransport.respondingWith(
            new HttpResponse(200, "{\"data\":{\"quote\":{\"price\":null}}}")
        );

        PriceState state = new CustomJsonProvider(defaultConfiguration(), transport).fetch(defaultRequest("BTC"));

        assertFalse(state.hasQuote());
        assertNotNull(state.error());
        assertEquals(ProviderError.Code.INVALID_RESPONSE, state.error().code());
        assertTrue(state.error().message().contains("price"));
    }

    @Test
    public void stringPriceReturnsProviderError() {
        RecordingTransport transport = RecordingTransport.respondingWith(
            new HttpResponse(200, "{\"data\":{\"quote\":{\"price\":\"123.45\"}}}")
        );

        PriceState state = new CustomJsonProvider(defaultConfiguration(), transport).fetch(defaultRequest("BTC"));

        assertFalse(state.hasQuote());
        assertNotNull(state.error());
        assertEquals(ProviderError.Code.INVALID_RESPONSE, state.error().code());
        assertTrue(state.error().message().contains("numeric"));
    }

    private static CustomJsonProvider.Configuration defaultConfiguration() {
        return CustomJsonProvider.Configuration.builder()
            .urlTemplate("https://example.test/quotes/{symbol}?apiKey={apiKey}")
            .header("Accept", "application/json")
            .pricePath("$.data.quote.price")
            .symbolPath("$.data.quote.symbol")
            .currencyPath("$.data.quote.currency")
            .timestampPath("$.data.quote.timestamp")
            .build();
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
        private HttpRequest lastRequest;

        private RecordingTransport(HttpResponse response) {
            this.response = response;
        }

        static RecordingTransport respondingWith(HttpResponse response) {
            return new RecordingTransport(response);
        }

        @Override
        public HttpResponse get(HttpRequest request, int timeoutMillis) throws IOException {
            this.lastRequest = request;
            return response;
        }
    }
}
