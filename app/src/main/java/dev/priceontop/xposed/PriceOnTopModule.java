package dev.priceontop.xposed;

import android.os.Build;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public final class PriceOnTopModule extends XposedModule {
    private static final String TAG = "PriceOnTop";
    private final HookFailurePolicy failurePolicy = HookFailurePolicy.withMaxFailures(3);
    private volatile String processName;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        failurePolicy.runSafely(() -> processName = param == null ? null : param.getProcessName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        failurePolicy.runSafely(() -> {
            String packageName = param == null ? null : param.getPackageName();
            if (!XposedScopeGuard.shouldRegister(packageName, processName)) {
                return;
            }

            RomFamily romFamily = RomFamily.detect(Build.FINGERPRINT, Build.MANUFACTURER, Build.BRAND);
            safeLog(Log.INFO, XposedHookDiagnostics.lifecycleEvent(
                "systemui-scope-accepted",
                packageName,
                processName,
                romFamily
            ));
            continueForSystemUi(packageName, processName, romFamily);
        });
    }

    private void continueForSystemUi(String packageName, String processName, RomFamily romFamily) {
        safeLog(Log.DEBUG, XposedHookDiagnostics.lifecycleEvent(
            "systemui-entry-ready",
            packageName,
            processName,
            romFamily
        ));
    }

    private void safeLog(int priority, String message) {
        try {
            log(priority, TAG, XposedHookDiagnostics.redact(message));
        } catch (Throwable ignored) {
        }
    }
}
