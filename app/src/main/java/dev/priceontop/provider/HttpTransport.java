package dev.priceontop.provider;

import java.io.IOException;

public interface HttpTransport {
    HttpResponse get(HttpRequest request, int timeoutMillis) throws IOException;

    final class TimeoutException extends IOException {
        public TimeoutException(String message) {
            super(message);
        }
    }
}
