package dev.priceontop.xposed;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class PriceOnTopModule extends XposedModule {
    private static final String TAG = "PriceOnTop";
    private final HookFailurePolicy failurePolicy = HookFailurePolicy.withMaxFailures(3);
    private final List<Object> hookHandles = new ArrayList<>();
    private volatile String processName;
    private volatile SystemUiPriceController controller;

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
            continueForSystemUi(param, packageName, processName, romFamily);
        });
    }

    private void continueForSystemUi(PackageLoadedParam param, String packageName, String processName, RomFamily romFamily) {
        safeLog(Log.DEBUG, XposedHookDiagnostics.lifecycleEvent(
            "systemui-entry-ready",
            packageName,
            processName,
            romFamily
        ));
        SystemUiPriceController activeController = controller();
        if (activeController == null) {
            safeLog(Log.WARN, "systemui-controller-unavailable");
            return;
        }
        registerClockHooks(param, romFamily, activeController);
    }

    private SystemUiPriceController controller() {
        SystemUiPriceController existing = controller;
        if (existing != null) {
            return existing;
        }
        Context context = currentProcessContext();
        if (context == null) {
            return null;
        }
        SystemUiPriceController created = SystemUiPriceController.fromContext(
            context,
            message -> safeLog(Log.DEBUG, message)
        );
        controller = created;
        return created;
    }

    private void registerClockHooks(PackageLoadedParam param, RomFamily romFamily, SystemUiPriceController activeController) {
        ClassLoader classLoader = param == null ? null : param.getDefaultClassLoader();
        if (classLoader == null) {
            safeLog(Log.WARN, "systemui-clock-hook-skipped missing-classloader");
            return;
        }
        for (String className : clockClassNames(romFamily)) {
            hookSetTextIfPresent(classLoader, className, romFamily, activeController);
        }
    }

    private void hookSetTextIfPresent(
        ClassLoader classLoader,
        String className,
        RomFamily romFamily,
        SystemUiPriceController activeController
    ) {
        try {
            Class<?> clockClass = Class.forName(className, false, classLoader);
            Method setText = findSetText(clockClass);
            if (setText == null) {
                return;
            }
            Object handle = hook(setText).intercept(chain -> {
                Object result = chain.proceed();
                failurePolicy.runSafely(() -> activeController.decorateClock(chain.getThisObject(), romFamily));
                return result;
            });
            hookHandles.add(handle);
            safeLog(Log.DEBUG, "systemui-clock-hook-installed class=" + className);
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable failure) {
            safeLog(Log.WARN, XposedHookDiagnostics.failure(failure));
        }
    }

    private static Method findSetText(Class<?> clockClass) {
        for (Method method : clockClass.getMethods()) {
            if (isSetTextMethod(method)) {
                method.setAccessible(true);
                return method;
            }
        }
        for (Method method : clockClass.getDeclaredMethods()) {
            if (isSetTextMethod(method)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static boolean isSetTextMethod(Method method) {
        if (!"setText".equals(method.getName()) || method.getParameterCount() != 1) {
            return false;
        }
        Class<?> parameterType = method.getParameterTypes()[0];
        return parameterType.isAssignableFrom(CharSequence.class) || parameterType.isAssignableFrom(String.class);
    }

    private static List<String> clockClassNames(RomFamily romFamily) {
        RomFamily family = romFamily == null ? RomFamily.UNKNOWN : romFamily;
        if (family == RomFamily.MIUI_HYPEROS) {
            return List.of(
                "com.android.systemui.statusbar.views.MiuiClock",
                "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarClock",
                "com.miui.systemui.statusbar.MiuiClock",
                "com.android.systemui.statusbar.policy.Clock",
                "com.android.systemui.statusbar.phone.Clock",
                "com.android.systemui.statusbar.phone.PhoneStatusBarClock"
            );
        }
        return List.of(
            "com.android.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.phone.Clock",
            "com.android.systemui.statusbar.phone.PhoneStatusBarClock"
        );
    }

    private static Context currentProcessContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getMethod("currentApplication");
            Object application = currentApplication.invoke(null);
            if (application instanceof Context context) {
                return applicationContextOrSelf(context);
            }
            Method currentActivityThread = activityThread.getMethod("currentActivityThread");
            Object thread = currentActivityThread.invoke(null);
            if (thread != null) {
                Method getSystemContext = activityThread.getMethod("getSystemContext");
                Object context = getSystemContext.invoke(thread);
                if (context instanceof Context systemContext) {
                    Context applicationContext = systemContext.getApplicationContext();
                    return applicationContext == null ? systemContext : applicationContext;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static Context applicationContextOrSelf(Context context) {
        Context applicationContext = context.getApplicationContext();
        return applicationContext == null ? context : applicationContext;
    }

    private void safeLog(int priority, String message) {
        try {
            log(priority, TAG, XposedHookDiagnostics.redact(message));
        } catch (Throwable ignored) {
        }
    }
}
