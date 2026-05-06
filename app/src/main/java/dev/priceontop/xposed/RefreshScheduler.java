package dev.priceontop.xposed;

import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;
import dev.priceontop.core.ProviderType;
import dev.priceontop.core.RefreshPolicy;
import dev.priceontop.provider.CustomJsonProvider;
import dev.priceontop.provider.FinnhubProvider;
import dev.priceontop.provider.HttpRequest;
import dev.priceontop.provider.HttpResponse;
import dev.priceontop.provider.HttpTransport;
import dev.priceontop.provider.PriceProvider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RefreshScheduler {
    public static final long MINIMUM_REFRESH_INTERVAL_MILLIS = RefreshPolicy.MIN_REFRESH_INTERVAL_SECONDS * 1_000L;
    public static final long MAXIMUM_REFRESH_INTERVAL_MILLIS = RefreshPolicy.MAX_REFRESH_INTERVAL_SECONDS * 1_000L;

    private static final int RATE_LIMIT_BACKOFF_MULTIPLIER = 2;

    private final ConfigSource configSource;
    private final StateStore stateStore;
    private final PriceProviderFactory providerFactory;
    private final BackgroundExecutor backgroundExecutor;
    private final Clock clock;
    private final Diagnostics diagnostics;
    private volatile long nextAllowedRefreshAtMillis;

    public RefreshScheduler(
        ConfigSource configSource,
        StateStore stateStore,
        PriceProviderFactory providerFactory,
        BackgroundExecutor backgroundExecutor,
        Clock clock,
        Diagnostics diagnostics
    ) {
        this.configSource = configSource;
        this.stateStore = stateStore;
        this.providerFactory = providerFactory;
        this.backgroundExecutor = backgroundExecutor;
        this.clock = clock;
        this.diagnostics = diagnostics == null ? Diagnostics.NO_OP : diagnostics;
    }

    public boolean requestRefresh() {
        PriceConfig config = safeLoadConfig();
        if (!isRefreshable(config)) {
            return false;
        }
        RefreshPolicy policy = effectivePolicy(config.refreshPolicy());
        long nowMillis = clock.nowMillis();
        synchronized (this) {
            if (nowMillis < nextAllowedRefreshAtMillis) {
                return false;
            }
            nextAllowedRefreshAtMillis = nowMillis + intervalMillis(policy);
        }
        try {
            backgroundExecutor.execute(() -> runRefresh(config, policy));
            return true;
        } catch (RuntimeException exception) {
            log("refresh schedule failed: " + XposedHookDiagnostics.failure(exception));
            return false;
        }
    }

    public long nextAllowedRefreshAtMillis() {
        return nextAllowedRefreshAtMillis;
    }

    public static RefreshPolicy effectivePolicy(RefreshPolicy policy) {
        RefreshPolicy candidate = policy == null ? RefreshPolicy.defaults() : policy;
        int intervalSeconds = clamp(
            candidate.refreshIntervalSeconds(),
            RefreshPolicy.MIN_REFRESH_INTERVAL_SECONDS,
            RefreshPolicy.MAX_REFRESH_INTERVAL_SECONDS
        );
        int timeoutMillis = candidate.timeoutMillis() <= 0
            ? RefreshPolicy.DEFAULT_TIMEOUT_MILLIS
            : Math.min(candidate.timeoutMillis(), RefreshPolicy.MAX_TIMEOUT_MILLIS);
        return RefreshPolicy.of(intervalSeconds, timeoutMillis);
    }

    public static PriceProviderFactory defaultProviderFactory(HttpTransport transport) {
        return config -> {
            HttpTransport resolvedTransport = transport == null ? new UrlConnectionTransport() : transport;
            if (config.providerType() == ProviderType.CUSTOM_JSON) {
                CustomJsonProvider.Configuration configuration = CustomJsonProvider.Configuration.builder()
                    .urlTemplate(config.customUrlTemplate())
                    .pricePath(config.customJsonPathPrice())
                    .symbolPath(config.customJsonPathSymbol())
                    .currencyPath(config.customJsonPathCurrency())
                    .timestampPath(config.customJsonPathTimestamp())
                    .build();
                return new CustomJsonProvider(configuration, resolvedTransport);
            }
            return new FinnhubProvider(resolvedTransport);
        };
    }

    public static BackgroundExecutor singleThreadExecutor() {
        ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PriceOnTopRefresh");
            thread.setDaemon(true);
            return thread;
        });
        return executorService::execute;
    }

    private void runRefresh(PriceConfig config, RefreshPolicy policy) {
        PriceProvider provider;
        try {
            provider = providerFactory == null ? null : providerFactory.create(config);
        } catch (RuntimeException exception) {
            log("refresh provider creation failed: " + XposedHookDiagnostics.failure(exception));
            return;
        }
        if (provider == null) {
            log("refresh skipped: missing provider");
            return;
        }
        PriceState previousState = safeLoadState();
        PriceProvider.Request request = PriceProvider.Request.builder()
            .symbol(config.symbol())
            .apiKey(config.apiKey())
            .refreshPolicy(policy)
            .nowMillis(clock.nowMillis())
            .build();
        PriceState fetchedState;
        try {
            fetchedState = provider.fetch(request);
        } catch (RuntimeException exception) {
            log("refresh fetch failed: " + XposedHookDiagnostics.failure(exception));
            return;
        }
        if (fetchedState == null) {
            log("refresh returned empty state");
            return;
        }
        if (fetchedState.hasQuote()) {
            safeSaveState(fetchedState);
            return;
        }
        if (isRateLimited(fetchedState)) {
            long backoffUntil = clock.nowMillis() + rateLimitBackoffMillis(policy);
            postponeUntil(backoffUntil);
            log("refresh rate-limited; keeping cached state; nextRefreshAtMillis=" + backoffUntil);
            return;
        }
        if (previousState != null && previousState.hasQuote()) {
            log("refresh error; keeping cached state; error=" + fetchedState.error());
            return;
        }
        safeSaveState(fetchedState);
    }

    private PriceConfig safeLoadConfig() {
        try {
            return configSource == null ? null : configSource.loadConfig();
        } catch (RuntimeException exception) {
            log("refresh config load failed: " + XposedHookDiagnostics.failure(exception));
            return null;
        }
    }

    private PriceState safeLoadState() {
        try {
            return stateStore == null ? PriceState.empty() : stateStore.loadState();
        } catch (RuntimeException exception) {
            log("refresh cache load failed: " + XposedHookDiagnostics.failure(exception));
            return PriceState.empty();
        }
    }

    private void safeSaveState(PriceState state) {
        try {
            if (stateStore != null) {
                stateStore.saveState(state);
            }
        } catch (RuntimeException exception) {
            log("refresh cache save failed: " + XposedHookDiagnostics.failure(exception));
        }
    }

    private void postponeUntil(long backoffUntilMillis) {
        synchronized (this) {
            if (backoffUntilMillis > nextAllowedRefreshAtMillis) {
                nextAllowedRefreshAtMillis = backoffUntilMillis;
            }
        }
    }

    private void log(String message) {
        diagnostics.log(XposedHookDiagnostics.redact(message));
    }

    private static boolean isRefreshable(PriceConfig config) {
        if (config == null || !config.enabled() || config.providerType() == null || config.symbol() == null || config.symbol().isBlank()) {
            return false;
        }
        if (config.providerType() == ProviderType.CUSTOM_JSON) {
            return config.customUrlTemplate() != null
                && !config.customUrlTemplate().isBlank()
                && config.customJsonPathPrice() != null
                && !config.customJsonPathPrice().isBlank();
        }
        return true;
    }

    private static boolean isRateLimited(PriceState state) {
        return state.error() != null && state.error().code() == ProviderError.Code.RATE_LIMIT;
    }

    private static long intervalMillis(RefreshPolicy policy) {
        return policy.refreshIntervalSeconds() * 1_000L;
    }

    private static long rateLimitBackoffMillis(RefreshPolicy policy) {
        return Math.min(MAXIMUM_REFRESH_INTERVAL_MILLIS, intervalMillis(policy) * RATE_LIMIT_BACKOFF_MULTIPLIER);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public interface ConfigSource {
        PriceConfig loadConfig();
    }

    public interface StateStore {
        PriceState loadState();

        void saveState(PriceState state);
    }

    public interface PriceProviderFactory {
        PriceProvider create(PriceConfig config);
    }

    public interface BackgroundExecutor {
        void execute(Runnable task);
    }

    public interface Clock {
        long nowMillis();
    }

    public interface Diagnostics {
        Diagnostics NO_OP = message -> { };

        void log(String message);
    }

    private static final class UrlConnectionTransport implements HttpTransport {
        @Override
        public HttpResponse get(HttpRequest request, int timeoutMillis) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
            connection.setRequestMethod(request.method());
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            for (Map.Entry<String, String> header : request.headers().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            int statusCode = connection.getResponseCode();
            try {
                return new HttpResponse(statusCode, readBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream()));
            } finally {
                connection.disconnect();
            }
        }

        private static String readBody(InputStream inputStream) throws IOException {
            if (inputStream == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            return builder.toString();
        }
    }
}
