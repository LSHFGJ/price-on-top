package dev.priceontop.storage;

import dev.priceontop.core.DiagnosticsRedactor;
import java.util.Arrays;

public final class ProviderAccess {
    public static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private ProviderAccess() {
    }

    public static boolean isAllowed(int callerUid, int ownUid, String[] packageNames) {
        if (callerUid == ownUid) {
            return true;
        }
        if (packageNames == null) {
            return false;
        }
        for (String packageName : packageNames) {
            if (SYSTEM_UI_PACKAGE.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static String deniedDiagnostic(int callerUid, String[] packageNames, String detail) {
        return DiagnosticsRedactor.redact(
            "provider access denied for uid=" + callerUid
                + " packages=" + Arrays.toString(packageNames == null ? new String[0] : packageNames)
                + " detail=" + detail
        );
    }
}
