package dev.priceontop.storage;

import android.os.Bundle;
import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;
import dev.priceontop.core.ProviderType;
import dev.priceontop.core.RefreshPolicy;
import java.util.List;
import java.util.Map;

public final class PriceTopContract {
    public static final String AUTHORITY = "dev.priceontop.provider";
    public static final String CONTENT_URI = "content://" + AUTHORITY;

    public static final String METHOD_GET_CONFIG = "get_config";
    public static final String METHOD_GET_REFRESH_CONFIG = "get_refresh_config";
    public static final String METHOD_GET_CACHE = "get_cache";
    public static final String METHOD_SAVE_CACHE = "save_cache";
    public static final String METHOD_SAVE_CONFIG = "save_config";

    public static final String KEY_STATUS = "status";
    public static final String KEY_ALLOWED = "allowed";
    public static final String KEY_PREFIX_CONFIG = "config.";
    public static final String KEY_PREFIX_CACHE = "cache.";
    public static final String KEY_SYSTEM_UI_HOOK_KILL_SWITCH = "systemUiHookKillSwitchEnabled";
    public static final String KEY_EXPERIMENTAL_PLACEMENT_ENABLED = "experimentalPlacementEnabled";

    private PriceTopContract() {
    }

    public static void putConfig(Bundle bundle, PriceConfig config, boolean includeSensitive) {
        if (bundle == null || config == null) {
            return;
        }
        putPrefixed(bundle, KEY_PREFIX_CONFIG, config.toIpcMap(includeSensitive));
    }

    public static void putSystemUiDefaults(Bundle bundle, boolean experimentalPlacementEnabled) {
        if (bundle == null) {
            return;
        }
        bundle.putString(KEY_PREFIX_CONFIG + KEY_SYSTEM_UI_HOOK_KILL_SWITCH, Boolean.toString(false));
        bundle.putString(KEY_PREFIX_CONFIG + KEY_EXPERIMENTAL_PLACEMENT_ENABLED, Boolean.toString(experimentalPlacementEnabled));
    }

    public static android.net.Uri contentUri() {
        return android.net.Uri.parse(CONTENT_URI);
    }

    public static boolean shouldRegisterSystemUiHooks(Bundle bundle) {
        try {
            return shouldRegisterSystemUiHooks(configFromBundle(bundle), systemUiHookKillSwitchEnabled(bundle));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static boolean shouldRegisterSystemUiHooks(PriceConfig config, boolean systemUiHookKillSwitchEnabled) {
        return SystemUiHookGate.shouldRegisterSystemUiHooks(config, systemUiHookKillSwitchEnabled);
    }

    public static boolean hasSystemUiConfig(Bundle bundle) {
        try {
            return configFromBundle(bundle) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static boolean systemUiHookKillSwitchEnabled(Bundle bundle) {
        return boolValue(bundle, KEY_PREFIX_CONFIG, KEY_SYSTEM_UI_HOOK_KILL_SWITCH, false);
    }

    public static boolean experimentalPlacementEnabled(Bundle bundle) {
        return boolValue(bundle, KEY_PREFIX_CONFIG, KEY_EXPERIMENTAL_PLACEMENT_ENABLED, false);
    }

    public static PriceConfig configFromBundle(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        String providerName = stringValue(bundle, KEY_PREFIX_CONFIG, "providerType");
        String symbol = stringValue(bundle, KEY_PREFIX_CONFIG, "symbol");
        if (providerName.isBlank() && symbol.isBlank()) {
            return null;
        }

        PriceConfig.Builder builder = PriceConfig.builder()
            .enabled(Boolean.parseBoolean(stringValue(bundle, KEY_PREFIX_CONFIG, "enabled")))
            .providerType(providerType(providerName))
            .apiKey(emptyToNull(stringValue(bundle, KEY_PREFIX_CONFIG, "apiKey")))
            .refreshIntervalSeconds(intValue(
                bundle,
                KEY_PREFIX_CONFIG,
                "refreshIntervalSeconds",
                RefreshPolicy.DEFAULT_REFRESH_INTERVAL_SECONDS
            ))
            .timeoutMillis(intValue(bundle, KEY_PREFIX_CONFIG, "timeoutMillis", RefreshPolicy.DEFAULT_TIMEOUT_MILLIS))
            .customUrlTemplate(emptyToNull(stringValue(bundle, KEY_PREFIX_CONFIG, "customUrlTemplate")))
            .customJsonPathPrice(emptyToNull(stringValue(bundle, KEY_PREFIX_CONFIG, "customJsonPathPrice")))
            .customJsonPathSymbol(emptyToNull(stringValue(bundle, KEY_PREFIX_CONFIG, "customJsonPathSymbol")))
            .customJsonPathCurrency(emptyToNull(stringValue(bundle, KEY_PREFIX_CONFIG, "customJsonPathCurrency")))
            .customJsonPathTimestamp(emptyToNull(stringValue(bundle, KEY_PREFIX_CONFIG, "customJsonPathTimestamp")));
        if (!symbol.isBlank()) {
            builder.symbols(List.of(symbol));
        }
        return builder.build();
    }

    public static void putState(Bundle bundle, PriceState state) {
        if (bundle == null) {
            return;
        }
        putPrefixed(bundle, KEY_PREFIX_CACHE, (state == null ? PriceState.empty() : state).toSanitizedMap());
    }

    public static PriceState stateFromBundle(Bundle bundle) {
        if (bundle == null) {
            return PriceState.empty();
        }
        String errorCode = stringValue(bundle, KEY_PREFIX_CACHE, "errorCode");
        if (!errorCode.isBlank()) {
            return PriceState.withError(ProviderError.of(
                providerErrorCode(errorCode),
                stringValue(bundle, KEY_PREFIX_CACHE, "errorMessage")
            ));
        }
        if (!Boolean.parseBoolean(stringValue(bundle, KEY_PREFIX_CACHE, "hasQuote"))) {
            return PriceState.empty();
        }
        String previousClose = stringValue(bundle, KEY_PREFIX_CACHE, "previousClose");
        PriceQuote quote = previousClose.isBlank()
            ? new PriceQuote(
                stringValue(bundle, KEY_PREFIX_CACHE, "symbol"),
                doubleValue(bundle, KEY_PREFIX_CACHE, "price", 0D),
                stringValue(bundle, KEY_PREFIX_CACHE, "currency"),
                longValue(bundle, KEY_PREFIX_CACHE, "quoteTimestampMillis", 0L)
            )
            : new PriceQuote(
                stringValue(bundle, KEY_PREFIX_CACHE, "symbol"),
                doubleValue(bundle, KEY_PREFIX_CACHE, "price", 0D),
                stringValue(bundle, KEY_PREFIX_CACHE, "currency"),
                longValue(bundle, KEY_PREFIX_CACHE, "quoteTimestampMillis", 0L),
                doubleValue(bundle, KEY_PREFIX_CACHE, "previousClose", Double.NaN)
            );
        return PriceState.withQuote(quote, longValue(bundle, KEY_PREFIX_CACHE, "fetchedAtMillis", 0L));
    }

    public static Bundle stateExtras(PriceState state) {
        Bundle bundle = new Bundle();
        putState(bundle, state);
        return bundle;
    }

    private static void putPrefixed(Bundle bundle, String prefix, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            bundle.putString(prefix + entry.getKey(), entry.getValue());
        }
    }

    private static String stringValue(Bundle bundle, String prefix, String key) {
        String value = bundle.getString(prefix + key, "");
        return value == null ? "" : value;
    }

    private static Integer intValue(Bundle bundle, String prefix, String key, int fallback) {
        try {
            return Integer.parseInt(stringValue(bundle, prefix, key));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean boolValue(Bundle bundle, String prefix, String key, boolean fallback) {
        String value = stringValue(bundle, prefix, key);
        if (value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value);
    }

    private static long longValue(Bundle bundle, String prefix, String key, long fallback) {
        try {
            return Long.parseLong(stringValue(bundle, prefix, key));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double doubleValue(Bundle bundle, String prefix, String key, double fallback) {
        try {
            return Double.parseDouble(stringValue(bundle, prefix, key));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static ProviderType providerType(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return null;
        }
        try {
            return ProviderType.valueOf(providerName);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ProviderError.Code providerErrorCode(String code) {
        try {
            return ProviderError.Code.valueOf(code);
        } catch (IllegalArgumentException exception) {
            return ProviderError.Code.UNKNOWN;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
