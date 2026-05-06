package dev.priceontop.xposed;

public final class XposedScopeGuard {
    public static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private XposedScopeGuard() {
    }

    public static boolean shouldRegister(String packageName, String processName) {
        try {
            return SYSTEM_UI_PACKAGE.equals(packageName) && SYSTEM_UI_PACKAGE.equals(processName);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
