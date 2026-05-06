package dev.priceontop.xposed;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.RefreshPolicy;
import dev.priceontop.format.PriceFormatter;
import dev.priceontop.storage.PriceTopContract;
import dev.priceontop.xposed.adapter.ClockTargetAdapter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class SystemUiPriceController {
    private final RefreshScheduler scheduler;
    private final RefreshScheduler.ConfigSource configSource;
    private final RefreshScheduler.StateStore stateStore;
    private final PriceFormatter formatter;
    private final RefreshScheduler.Clock clock;
    private final UiMutationDispatcher uiMutationDispatcher;
    private final List<ClockTargetAdapter> adapters;
    private final ClockTextDecorator decorator;
    private final RefreshScheduler.Diagnostics diagnostics;
    private final AtomicReference<String> cachedDisplayText = new AtomicReference<>("");

    public SystemUiPriceController(
        RefreshScheduler scheduler,
        RefreshScheduler.ConfigSource configSource,
        RefreshScheduler.StateStore stateStore,
        PriceFormatter formatter,
        RefreshScheduler.Clock clock,
        UiMutationDispatcher uiMutationDispatcher,
        List<ClockTargetAdapter> adapters,
        ClockTextDecorator decorator,
        RefreshScheduler.Diagnostics diagnostics
    ) {
        this.scheduler = scheduler;
        this.configSource = configSource;
        this.stateStore = stateStore;
        this.formatter = formatter == null ? new PriceFormatter() : formatter;
        this.clock = clock == null ? System::currentTimeMillis : clock;
        this.uiMutationDispatcher = uiMutationDispatcher == null ? targetPostDispatcher() : uiMutationDispatcher;
        this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
        this.decorator = decorator == null ? new ClockTextDecorator() : decorator;
        this.diagnostics = diagnostics == null ? RefreshScheduler.Diagnostics.NO_OP : diagnostics;
    }

    public void decorateClock(Object target, RomFamily romFamily) {
        refreshCachedDisplayText();
        if (scheduler != null) {
            scheduler.requestRefresh();
        }
        String displayText = cachedDisplayText.get();
        RomFamily family = romFamily == null ? RomFamily.UNKNOWN : romFamily;
        uiMutationDispatcher.post(target, () -> decorateOnUiThread(target, family, displayText));
    }

    public String cachedDisplayText() {
        return cachedDisplayText.get();
    }

    public void refreshCachedDisplayText() {
        try {
            PriceConfig config = configSource == null ? null : configSource.loadConfig();
            if (config == null || !config.enabled()) {
                cachedDisplayText.set("");
                return;
            }
            RefreshPolicy policy = RefreshScheduler.effectivePolicy(config.refreshPolicy());
            PriceState state = stateStore == null ? PriceState.empty() : stateStore.loadState();
            cachedDisplayText.set(formatter.format(state, clock.nowMillis(), policy));
        } catch (RuntimeException exception) {
            cachedDisplayText.set("");
            log("controller cache display update failed: " + XposedHookDiagnostics.failure(exception));
        }
    }

    private void decorateOnUiThread(Object target, RomFamily romFamily, String displayText) {
        List<ClockTargetAdapter> candidates = adaptersFor(romFamily);
        for (ClockTargetAdapter adapter : candidates) {
            if (!adapter.supportsRom(romFamily)) {
                continue;
            }
            ClockTargetAdapter.Result result = adapter.decorate(target, displayText, decorator);
            if (result.isSupported()) {
                return;
            }
        }
        log("controller skipped unsupported clock target");
    }

    private List<ClockTargetAdapter> adaptersFor(RomFamily romFamily) {
        if (!adapters.isEmpty()) {
            return adapters;
        }
        return ClockTargetAdapter.orderedFor(romFamily);
    }

    private void log(String message) {
        diagnostics.log(XposedHookDiagnostics.redact(message));
    }

    public static UiMutationDispatcher targetPostDispatcher() {
        return (target, mutation) -> {
            if (target != null) {
                postToTarget(target, mutation);
            }
        };
    }

    private static boolean postToTarget(Object target, Runnable mutation) {
        try {
            Method post = target.getClass().getMethod("post", Runnable.class);
            Object result = post.invoke(target, mutation);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public interface UiMutationDispatcher {
        void post(Object target, Runnable mutation);
    }

    public static final class AndroidProviderGateway implements RefreshScheduler.ConfigSource, RefreshScheduler.StateStore {
        private final ContentResolver contentResolver;
        private final RefreshScheduler.Diagnostics diagnostics;

        public AndroidProviderGateway(ContentResolver contentResolver, RefreshScheduler.Diagnostics diagnostics) {
            this.contentResolver = contentResolver;
            this.diagnostics = diagnostics == null ? RefreshScheduler.Diagnostics.NO_OP : diagnostics;
        }

        @Override
        public PriceConfig loadConfig() {
            Bundle bundle = call(PriceTopContract.METHOD_GET_REFRESH_CONFIG, null);
            return PriceTopContract.configFromBundle(bundle);
        }

        @Override
        public PriceState loadState() {
            Bundle bundle = call(PriceTopContract.METHOD_GET_CACHE, null);
            return PriceTopContract.stateFromBundle(bundle);
        }

        @Override
        public void saveState(PriceState state) {
            call(PriceTopContract.METHOD_SAVE_CACHE, PriceTopContract.stateExtras(state));
        }

        private Bundle call(String method, Bundle extras) {
            try {
                if (contentResolver == null) {
                    return null;
                }
                return contentResolver.call(PriceTopContract.CONTENT_URI, method, null, extras);
            } catch (RuntimeException exception) {
                diagnostics.log(XposedHookDiagnostics.failure(exception));
                return null;
            }
        }
    }

    public static SystemUiPriceController fromContext(Context context, RefreshScheduler.Diagnostics diagnostics) {
        AndroidProviderGateway gateway = new AndroidProviderGateway(
            context == null ? null : context.getContentResolver(),
            diagnostics
        );
        RefreshScheduler scheduler = new RefreshScheduler(
            gateway,
            gateway,
            RefreshScheduler.defaultProviderFactory(null),
            RefreshScheduler.singleThreadExecutor(),
            System::currentTimeMillis,
            diagnostics
        );
        return new SystemUiPriceController(
            scheduler,
            gateway,
            gateway,
            new PriceFormatter(),
            System::currentTimeMillis,
            targetPostDispatcher(),
            new ArrayList<>(),
            new ClockTextDecorator(),
            diagnostics
        );
    }
}
