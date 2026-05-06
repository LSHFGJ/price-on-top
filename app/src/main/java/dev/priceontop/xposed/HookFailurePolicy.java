package dev.priceontop.xposed;

public final class HookFailurePolicy {
    public interface HookAttempt {
        void run() throws Throwable;
    }

    private final int maxFailures;
    private int failureCount;
    private boolean disabled;
    private String lastFailureDiagnostic = "";

    private HookFailurePolicy(int maxFailures) {
        this.maxFailures = Math.max(1, maxFailures);
    }

    public static HookFailurePolicy withMaxFailures(int maxFailures) {
        return new HookFailurePolicy(maxFailures);
    }

    public synchronized boolean runSafely(HookAttempt attempt) {
        if (disabled) {
            return false;
        }
        if (attempt == null) {
            recordFailure(new NullPointerException("missing hook attempt"));
            return false;
        }

        try {
            attempt.run();
            return true;
        } catch (Throwable failure) {
            recordFailure(failure);
            return false;
        }
    }

    public synchronized void recordFailure(Throwable failure) {
        try {
            failureCount++;
            lastFailureDiagnostic = XposedHookDiagnostics.failure(failure);
            if (failureCount >= maxFailures) {
                disabled = true;
            }
        } catch (RuntimeException exception) {
            disabled = true;
            lastFailureDiagnostic = "hook failure: redacted";
        }
    }

    public synchronized boolean isDisabled() {
        return disabled;
    }

    public synchronized int failureCount() {
        return failureCount;
    }

    public synchronized String lastFailureDiagnostic() {
        return lastFailureDiagnostic;
    }
}
