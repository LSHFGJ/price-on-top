package dev.priceontop.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;
import dev.priceontop.core.ProviderType;
import dev.priceontop.provider.PriceProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class RefreshSchedulerTest {
    private static final String REDACTION_SENTINEL = "task7-redaction-sentinel";
    private static final long NOW_MILLIS = 1_720_000_000_000L;

    @Test
    public void clampsRefreshIntervalBelowSixtySeconds() {
        FakeClock clock = new FakeClock(NOW_MILLIS);
        RecordingExecutor executor = new RecordingExecutor();
        FakeStateStore stateStore = new FakeStateStore(PriceState.empty());
        RecordingProvider provider = new RecordingProvider(successState(NOW_MILLIS));
        RefreshScheduler scheduler = scheduler(configWithRefreshSeconds(1), stateStore, provider, executor, clock, new RecordingDiagnostics());

        assertTrue(scheduler.requestRefresh());
        assertEquals(1, executor.pendingCount());
        assertFalse(scheduler.requestRefresh());

        clock.advanceMillis(59_000L);
        assertFalse(scheduler.requestRefresh());

        clock.advanceMillis(1_000L);
        assertTrue(scheduler.requestRefresh());
    }

    @Test
    public void http429BackoffKeepsLastGood() {
        FakeClock clock = new FakeClock(NOW_MILLIS);
        RecordingExecutor executor = new RecordingExecutor();
        PriceState lastGood = successState(NOW_MILLIS - 5_000L);
        FakeStateStore stateStore = new FakeStateStore(lastGood);
        RecordingProvider provider = new RecordingProvider(PriceState.withError(
            ProviderError.of(ProviderError.Code.RATE_LIMIT, REDACTION_SENTINEL + " was rate limited")
        ));
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        RefreshScheduler scheduler = scheduler(configWithRefreshSeconds(60), stateStore, provider, executor, clock, diagnostics);

        assertTrue(scheduler.requestRefresh());
        assertEquals(0, provider.fetchCount);

        executor.runNext();

        assertEquals(1, provider.fetchCount);
        assertTrue(stateStore.currentState.hasQuote());
        assertEquals("BTC", stateStore.currentState.quote().symbol());
        assertEquals(65_000d, stateStore.currentState.quote().price(), 0.000001d);
        assertEquals(0, stateStore.saveCount);
        assertTrue(scheduler.nextAllowedRefreshAtMillis() - clock.nowMillis() >= 120_000L);
        assertFalse(diagnostics.joined().contains(REDACTION_SENTINEL));
    }

    @Test
    public void fetchRunsOnlyWhenBackgroundExecutorRuns() {
        FakeClock clock = new FakeClock(NOW_MILLIS);
        RecordingExecutor executor = new RecordingExecutor();
        FakeStateStore stateStore = new FakeStateStore(PriceState.empty());
        RecordingProvider provider = new RecordingProvider(successState(NOW_MILLIS));
        RefreshScheduler scheduler = scheduler(configWithRefreshSeconds(60), stateStore, provider, executor, clock, new RecordingDiagnostics());

        assertTrue(scheduler.requestRefresh());

        assertEquals(0, provider.fetchCount);
        assertEquals(1, executor.pendingCount());

        executor.runNext();

        assertEquals(1, provider.fetchCount);
        assertEquals(1, stateStore.saveCount);
        assertEquals(RefreshScheduler.MINIMUM_REFRESH_INTERVAL_MILLIS, provider.lastRequest.refreshPolicy().refreshIntervalSeconds() * 1_000L);
    }

    @Test
    public void providerFactoryExceptionDoesNotEscapeQueuedRefreshTask() {
        FakeClock clock = new FakeClock(NOW_MILLIS);
        RecordingExecutor executor = new RecordingExecutor();
        FakeStateStore stateStore = new FakeStateStore(successState(NOW_MILLIS - 5_000L));
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        RefreshScheduler scheduler = new RefreshScheduler(
            () -> configWithRefreshSeconds(60),
            stateStore,
            ignoredConfig -> {
                throw new IllegalStateException("factory failed apiKey=" + REDACTION_SENTINEL);
            },
            executor,
            clock,
            diagnostics
        );

        assertTrue(scheduler.requestRefresh());
        executor.runNext();

        assertEquals(0, stateStore.saveCount);
        assertTrue(stateStore.currentState.hasQuote());
        assertFalse(diagnostics.joined().contains(REDACTION_SENTINEL));
        assertTrue(diagnostics.joined().contains("***"));
    }

    private static RefreshScheduler scheduler(
        PriceConfig config,
        FakeStateStore stateStore,
        RecordingProvider provider,
        RecordingExecutor executor,
        FakeClock clock,
        RecordingDiagnostics diagnostics
    ) {
        return new RefreshScheduler(
            () -> config,
            stateStore,
            ignoredConfig -> provider,
            executor,
            clock,
            diagnostics
        );
    }

    private static PriceConfig configWithRefreshSeconds(int refreshSeconds) {
        return PriceConfig.builder()
            .enabled(true)
            .providerType(ProviderType.FINNHUB)
            .symbols(List.of("BTC"))
            .apiKey(REDACTION_SENTINEL)
            .refreshIntervalSeconds(refreshSeconds)
            .timeoutMillis(3_000)
            .build();
    }

    private static PriceState successState(long fetchedAtMillis) {
        return PriceState.withQuote(new PriceQuote("BTC", 65_000d, "$", fetchedAtMillis), fetchedAtMillis);
    }

    private static final class FakeClock implements RefreshScheduler.Clock {
        private long nowMillis;

        private FakeClock(long nowMillis) {
            this.nowMillis = nowMillis;
        }

        @Override
        public long nowMillis() {
            return nowMillis;
        }

        void advanceMillis(long deltaMillis) {
            nowMillis += deltaMillis;
        }
    }

    private static final class RecordingExecutor implements RefreshScheduler.BackgroundExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        int pendingCount() {
            return tasks.size();
        }

        void runNext() {
            tasks.removeFirst().run();
        }
    }

    private static final class FakeStateStore implements RefreshScheduler.StateStore {
        private PriceState currentState;
        private int saveCount;

        private FakeStateStore(PriceState currentState) {
            this.currentState = currentState;
        }

        @Override
        public PriceState loadState() {
            return currentState;
        }

        @Override
        public void saveState(PriceState state) {
            saveCount++;
            currentState = state;
        }
    }

    private static final class RecordingProvider implements PriceProvider {
        private final PriceState nextState;
        private int fetchCount;
        private Request lastRequest;

        private RecordingProvider(PriceState nextState) {
            this.nextState = nextState;
        }

        @Override
        public ProviderType type() {
            return ProviderType.FINNHUB;
        }

        @Override
        public PriceState fetch(Request request) {
            fetchCount++;
            lastRequest = request;
            return nextState;
        }
    }

    private static final class RecordingDiagnostics implements RefreshScheduler.Diagnostics {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }

        String joined() {
            return String.join("\n", messages);
        }
    }
}
