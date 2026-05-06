package dev.priceontop.provider;

import dev.priceontop.core.DiagnosticsRedactor;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;

final class ProviderDiagnostics {
    private ProviderDiagnostics() {
    }

    static PriceState error(ProviderError.Code code, String message, String... rawSecrets) {
        return PriceState.withError(ProviderError.of(code, sanitize(message, rawSecrets)));
    }

    static String sanitize(String value, String... rawSecrets) {
        return DiagnosticsRedactor.redact(value, rawSecrets);
    }

    static ProviderError.Code codeForHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return ProviderError.Code.UNAUTHORIZED;
        }
        if (statusCode == 429) {
            return ProviderError.Code.RATE_LIMIT;
        }
        return ProviderError.Code.NETWORK;
    }

    static String messageForHttpStatus(String providerName, HttpResponse response, HttpRequest request) {
        String prefix;
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            prefix = providerName + " unauthorized";
        } else if (response.statusCode() == 429) {
            prefix = providerName + " rate limited";
        } else {
            prefix = providerName + " HTTP " + response.statusCode();
        }
        return prefix + " for " + request.toSanitizedString() + " body=" + response.body();
    }
}
