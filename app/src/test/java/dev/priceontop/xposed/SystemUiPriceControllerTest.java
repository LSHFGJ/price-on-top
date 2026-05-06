package dev.priceontop.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderType;
import dev.priceontop.format.PriceFormatter;
import dev.priceontop.provider.PriceProvider;
import dev.priceontop.xposed.adapter.AospClockAdapter;
import dev.priceontop.xposed.adapter.ClockTargetAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class SystemUiPriceControllerTest {
    private static final String REDACTION_SENTINEL = "controller-redaction-sentinel";
    private static final long NOW_MILLIS = 1_720_000_000_000L;

    @Test
    public void usesCachedValueForUiThread() {
        RecordingExecutor backgroundExecutor = new RecordingExecutor();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        FakeStateStore stateStore = new FakeStateStore(successState(NOW_MILLIS));
        RecordingProvider provider = new RecordingProvider(successState(NOW_MILLIS + 1_000L));
        SystemUiPriceController controller = controller(stateStore, provider, backgroundExecutor, dispatcher, new RecordingDiagnostics());
        FakeClockTarget target = new FakeClockTarget("12:30");

        controller.decorateClock(target, RomFamily.AOSP_PIXEL_LINEAGE);

        assertEquals("12:30", target.getText().toString());
        assertEquals(1, dispatcher.pendingCount());
        assertEquals(1, backgroundExecutor.pendingCount());
        assertEquals(0, provider.fetchCount);

        dispatcher.runNext();

        assertEquals("12:30 · BTC $65,000", target.getText().toString());

        backgroundExecutor.runNext();

        assertEquals(1, provider.fetchCount);
    }

    @Test
    public void staleOverThirtyMinutesLeavesClockUnchanged() {
        RecordingExecutor backgroundExecutor = new RecordingExecutor();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        FakeStateStore stateStore = new FakeStateStore(successState(NOW_MILLIS - (31 * 60 * 1_000L)));
        RecordingProvider provider = new RecordingProvider(successState(NOW_MILLIS));
        SystemUiPriceController controller = controller(stateStore, provider, backgroundExecutor, dispatcher, new RecordingDiagnostics());
        FakeClockTarget target = new FakeClockTarget("12:30 · BTC $65,000");

        controller.decorateClock(target, RomFamily.AOSP_PIXEL_LINEAGE);
        dispatcher.runNext();

        assertEquals("12:30", target.getText().toString());
    }

    @Test
    public void controllerDiagnosticsRedactApiKey() {
        RecordingExecutor backgroundExecutor = new RecordingExecutor();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        FakeStateStore stateStore = new FakeStateStore(successState(NOW_MILLIS));
        stateStore.loadFailure = new IllegalStateException("cache failed apiKey=" + REDACTION_SENTINEL);
        RecordingProvider provider = new RecordingProvider(successState(NOW_MILLIS));
        SystemUiPriceController controller = controller(stateStore, provider, backgroundExecutor, dispatcher, diagnostics);

        controller.decorateClock(new FakeClockTarget("12:30"), RomFamily.AOSP_PIXEL_LINEAGE);

        assertFalse(diagnostics.joined().contains(REDACTION_SENTINEL));
        assertTrue(diagnostics.joined().contains("***"));
    }

    @Test
    public void targetPostDispatcherPostsWithoutSynchronousFallback() {
        SystemUiPriceController.UiMutationDispatcher dispatcher = SystemUiPriceController.targetPostDispatcher();
        int[] mutationCount = {0};

        dispatcher.post(new PostingTarget(), () -> mutationCount[0]++);
        dispatcher.post(new RejectingPostTarget(), () -> mutationCount[0] += 10);
        dispatcher.post(new NoPostTarget(), () -> mutationCount[0] += 100);

        assertEquals(1, mutationCount[0]);
    }

    @Test
    public void moduleConnectsControllerWithoutDirectHookHttp() throws IOException {
        String source = readSource("PriceOnTopModule.java");
        String onPackageLoadedBody = between(source, "public void onPackageLoaded", "private void continueForSystemUi");

        assertTrue(source.contains("SystemUiPriceController"));
        assertFalse(onPackageLoadedBody.contains("FinnhubProvider"));
        assertFalse(onPackageLoadedBody.contains("CustomJsonProvider"));
        assertFalse(onPackageLoadedBody.contains("HttpTransport"));
        assertFalse(onPackageLoadedBody.contains("fetch("));
        assertFalse(onPackageLoadedBody.contains("HttpURLConnection"));
    }

    @Test
    public void moduleHookContainsControllerFailureContainment() throws IOException {
        String source = readSource("PriceOnTopModule.java");
        String hookBody = between(source, "hook(setText).intercept", "hookHandles.add");

        assertTrue(hookBody.contains("failurePolicy.runSafely"));
        assertTrue(hookBody.contains("activeController.decorateClock"));
    }

    private static SystemUiPriceController controller(
        FakeStateStore stateStore,
        RecordingProvider provider,
        RecordingExecutor backgroundExecutor,
        RecordingDispatcher dispatcher,
        RecordingDiagnostics diagnostics
    ) {
        FakeClock clock = new FakeClock(NOW_MILLIS);
        PriceConfig config = config();
        RefreshScheduler scheduler = new RefreshScheduler(
            () -> config,
            stateStore,
            ignoredConfig -> provider,
            backgroundExecutor,
            clock,
            diagnostics
        );
        return new SystemUiPriceController(
            scheduler,
            () -> config,
            stateStore,
            new PriceFormatter(),
            clock,
            dispatcher,
            List.of(new AospClockAdapter()),
            new ClockTextDecorator(),
            diagnostics
        );
    }

    private static PriceConfig config() {
        return PriceConfig.builder()
            .enabled(true)
            .providerType(ProviderType.FINNHUB)
            .symbols(List.of("BTC"))
            .apiKey(REDACTION_SENTINEL)
            .refreshIntervalSeconds(60)
            .timeoutMillis(3_000)
            .build();
    }

    private static PriceState successState(long fetchedAtMillis) {
        return PriceState.withQuote(new PriceQuote("BTC", 65_000d, "$", fetchedAtMillis), fetchedAtMillis);
    }

    private static String readSource(String fileName) throws IOException {
        Path sourcePath = Path.of("src", "main", "java", "dev", "priceontop", "xposed", fileName);
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker);
        return start < 0 || end < 0 || end <= start ? source : source.substring(start, end);
    }

    private static final class FakeClock implements RefreshScheduler.Clock {
        private final long nowMillis;

        private FakeClock(long nowMillis) {
            this.nowMillis = nowMillis;
        }

        @Override
        public long nowMillis() {
            return nowMillis;
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

    private static final class RecordingDispatcher implements SystemUiPriceController.UiMutationDispatcher {
        private final ArrayDeque<Runnable> mutations = new ArrayDeque<>();

        @Override
        public void post(Object target, Runnable mutation) {
            mutations.add(mutation);
        }

        int pendingCount() {
            return mutations.size();
        }

        void runNext() {
            mutations.removeFirst().run();
        }
    }

    private static final class FakeStateStore implements RefreshScheduler.StateStore {
        private PriceState state;
        private RuntimeException loadFailure;

        private FakeStateStore(PriceState state) {
            this.state = state;
        }

        @Override
        public PriceState loadState() {
            if (loadFailure != null) {
                throw loadFailure;
            }
            return state;
        }

        @Override
        public void saveState(PriceState state) {
            this.state = state;
        }
    }

    private static final class RecordingProvider implements PriceProvider {
        private final PriceState nextState;
        private int fetchCount;

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

    private static final class FakeClockTarget implements ClockTargetAdapter.MutableClockTarget {
        private CharSequence text;

        private FakeClockTarget(CharSequence text) {
            this.text = text;
        }

        @Override
        public String targetClassName() {
            return "com.android.systemui.statusbar.policy.Clock";
        }

        @Override
        public ClockTargetAdapter.TargetKind targetKind() {
            return ClockTargetAdapter.TargetKind.COLLAPSED_STATUS_BAR;
        }

        @Override
        public CharSequence getText() {
            return text;
        }

        @Override
        public void setText(CharSequence text) {
            this.text = text;
        }
    }

    public static final class PostingTarget {
        public boolean post(Runnable mutation) {
            mutation.run();
            return true;
        }
    }

    public static final class RejectingPostTarget {
        public boolean post(Runnable mutation) {
            return false;
        }
    }

    public static final class NoPostTarget {
    }
}
