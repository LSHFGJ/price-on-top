package dev.priceontop.core;

import java.util.ArrayList;
import java.util.List;

public final class RefreshPolicy {
    public static final int MIN_REFRESH_INTERVAL_SECONDS = 60;
    public static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 120;
    public static final int MAX_REFRESH_INTERVAL_SECONDS = 900;
    public static final int DEFAULT_TIMEOUT_MILLIS = 3_000;
    public static final int MAX_TIMEOUT_MILLIS = 5_000;
    public static final long STALE_THRESHOLD_MILLIS = 5 * 60 * 1000L;
    public static final long HIDE_THRESHOLD_MILLIS = 30 * 60 * 1000L;

    private final int refreshIntervalSeconds;
    private final int timeoutMillis;

    private RefreshPolicy(int refreshIntervalSeconds, int timeoutMillis) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
        this.timeoutMillis = timeoutMillis;
    }

    public static RefreshPolicy defaults() {
        return new RefreshPolicy(DEFAULT_REFRESH_INTERVAL_SECONDS, DEFAULT_TIMEOUT_MILLIS);
    }

    public static RefreshPolicy of(Integer refreshIntervalSeconds, Integer timeoutMillis) {
        return new RefreshPolicy(
            refreshIntervalSeconds == null ? DEFAULT_REFRESH_INTERVAL_SECONDS : refreshIntervalSeconds,
            timeoutMillis == null ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis
        );
    }

    public int refreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public int timeoutMillis() {
        return timeoutMillis;
    }

    public long staleThresholdMillis() {
        return STALE_THRESHOLD_MILLIS;
    }

    public long hideThresholdMillis() {
        return HIDE_THRESHOLD_MILLIS;
    }

    List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (refreshIntervalSeconds < MIN_REFRESH_INTERVAL_SECONDS || refreshIntervalSeconds > MAX_REFRESH_INTERVAL_SECONDS) {
            errors.add("refresh interval must be between 60 and 900 seconds");
        }
        if (timeoutMillis <= 0) {
            errors.add("timeout must be positive");
        }
        if (timeoutMillis > MAX_TIMEOUT_MILLIS) {
            errors.add("timeout must be at most 5000 milliseconds");
        }
        return errors;
    }

    @Override
    public String toString() {
        return "RefreshPolicy{refreshIntervalSeconds=" + refreshIntervalSeconds
            + ", timeoutMillis=" + timeoutMillis
            + ", staleThresholdMillis=" + STALE_THRESHOLD_MILLIS
            + ", hideThresholdMillis=" + HIDE_THRESHOLD_MILLIS
            + '}';
    }
}
