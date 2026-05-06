package dev.priceontop.provider;

import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;
import dev.priceontop.core.ProviderType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

public final class FinnhubProvider implements PriceProvider {
    private static final String ENDPOINT = "https://finnhub.io/api/v1/quote";
    private final HttpTransport transport;

    public FinnhubProvider(HttpTransport transport) {
        this.transport = transport;
    }

    @Override
    public ProviderType type() {
        return ProviderType.FINNHUB;
    }

    @Override
    public PriceState fetch(Request request) {
        HttpRequest httpRequest = HttpRequest.get(buildUrl(request), Map.of(), request.apiKey());
        try {
            HttpResponse response = transport.get(httpRequest, request.refreshPolicy().timeoutMillis());
            if (response.statusCode() != 200) {
                return ProviderDiagnostics.error(
                    ProviderDiagnostics.codeForHttpStatus(response.statusCode()),
                    ProviderDiagnostics.messageForHttpStatus("Finnhub", response, httpRequest),
                    request.apiKey()
                );
            }
            return parseQuote(response.body(), request);
        } catch (HttpTransport.TimeoutException exception) {
            return ProviderDiagnostics.error(ProviderError.Code.TIMEOUT, "Finnhub timeout: " + exception.getMessage(), request.apiKey());
        } catch (IOException exception) {
            return ProviderDiagnostics.error(ProviderError.Code.NETWORK, "Finnhub network error: " + exception.getMessage(), request.apiKey());
        } catch (IllegalArgumentException exception) {
            return ProviderDiagnostics.error(ProviderError.Code.INVALID_RESPONSE, exception.getMessage(), request.apiKey());
        }
    }

    private PriceState parseQuote(String body, Request request) {
        Object json = SimpleJsonParser.parse(body);
        double current = requiredNumber(json, "$.c", "current price");
        Double previousClose = optionalNumber(json, "$.pc", "previous close");
        Long timestampMillis = optionalTimestampMillis(json, "$.t");
        PriceQuote quote = new PriceQuote(
            request.symbol(),
            current,
            request.displayCurrencyOr("$"),
            timestampMillis == null ? request.nowMillis() : timestampMillis,
            previousClose
        );
        return PriceState.withQuote(quote, request.nowMillis());
    }

    private static String buildUrl(Request request) {
        return ENDPOINT
            + "?symbol=" + encode(request.symbol())
            + "&token=" + encode(request.apiKey());
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 encoding is unavailable", exception);
        }
    }

    static double requiredNumber(Object json, String path, String label) {
        SimpleJsonPath.Result result = SimpleJsonPath.read(json, path);
        if (!result.found()) {
            throw new IllegalArgumentException("missing " + label + " at " + path);
        }
        if (result.value() == null) {
            throw new IllegalArgumentException(label + " at " + path + " is null");
        }
        if (!(result.value() instanceof Number number)) {
            throw new IllegalArgumentException(label + " at " + path + " must be numeric");
        }
        return number.doubleValue();
    }

    static Double optionalNumber(Object json, String path, String label) {
        SimpleJsonPath.Result result = SimpleJsonPath.read(json, path);
        if (!result.found() || result.value() == null) {
            return null;
        }
        if (!(result.value() instanceof Number number)) {
            throw new IllegalArgumentException(label + " at " + path + " must be numeric");
        }
        return number.doubleValue();
    }

    static Long optionalTimestampMillis(Object json, String path) {
        Double timestamp = optionalNumber(json, path, "timestamp");
        if (timestamp == null) {
            return null;
        }
        long rounded = timestamp.longValue();
        return rounded < 1_000_000_000_000L ? rounded * 1_000L : rounded;
    }
}
