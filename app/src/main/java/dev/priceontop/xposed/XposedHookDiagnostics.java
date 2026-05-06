package dev.priceontop.xposed;

import dev.priceontop.core.DiagnosticsRedactor;

public final class XposedHookDiagnostics {
    private XposedHookDiagnostics() {
    }

    public static String redact(String value) {
        try {
            return DiagnosticsRedactor.redact(value);
        } catch (RuntimeException exception) {
            return "diagnostic redaction failed";
        }
    }

    public static String failure(Throwable failure) {
        try {
            if (failure == null) {
                return "hook failure: unknown";
            }
            return redact("hook failure: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } catch (RuntimeException exception) {
            return "hook failure: redacted";
        }
    }

    public static String lifecycleEvent(
        String event,
        String packageName,
        String processName,
        RomFamily romFamily
    ) {
        try {
            return redact("event=" + event
                + " package=" + packageName
                + " process=" + processName
                + " romFamily=" + (romFamily == null ? RomFamily.UNKNOWN : romFamily));
        } catch (RuntimeException exception) {
            return "event=redacted";
        }
    }
}
