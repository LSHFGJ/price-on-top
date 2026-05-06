package dev.priceontop.storage;

import android.content.Context;
import android.content.SharedPreferences;
import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.PriceQuote;
import dev.priceontop.core.PriceState;
import dev.priceontop.core.ProviderError;
import dev.priceontop.core.ProviderType;
import java.util.List;

public final class AndroidPrivatePriceStorage implements PriceStorage {
    private static final String PREFS_NAME = "price_on_top_private";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_SYMBOL = "symbol";
    private static final String KEY_API_KEY = "apiKey";
    private static final String KEY_REFRESH_SECONDS = "refreshIntervalSeconds";
    private static final String KEY_TIMEOUT_MILLIS = "timeoutMillis";
    private static final String KEY_CUSTOM_URL = "customUrlTemplate";
    private static final String KEY_CUSTOM_PRICE = "customJsonPathPrice";
    private static final String KEY_CUSTOM_SYMBOL = "customJsonPathSymbol";
    private static final String KEY_CUSTOM_CURRENCY = "customJsonPathCurrency";
    private static final String KEY_CUSTOM_TIMESTAMP = "customJsonPathTimestamp";

    private static final String KEY_CACHE_HAS_QUOTE = "cache.hasQuote";
    private static final String KEY_CACHE_SYMBOL = "cache.symbol";
    private static final String KEY_CACHE_PRICE = "cache.price";
    private static final String KEY_CACHE_CURRENCY = "cache.currency";
    private static final String KEY_CACHE_TIMESTAMP = "cache.timestampMillis";
    private static final String KEY_CACHE_FETCHED_AT = "cache.fetchedAtMillis";
    private static final String KEY_CACHE_ERROR_CODE = "cache.errorCode";
    private static final String KEY_CACHE_ERROR_MESSAGE = "cache.errorMessage";

    private final Context context;

    public AndroidPrivatePriceStorage(Context context) {
        this.context = context;
    }

    @Override
    public PriceConfig loadConfig() {
        SharedPreferences preferences = preferences();
        String providerName = preferences.getString(KEY_PROVIDER, null);
        String symbol = preferences.getString(KEY_SYMBOL, null);
        if (providerName == null && symbol == null) {
            return null;
        }

        ProviderType providerType = null;
        if (providerName != null) {
            providerType = ProviderType.valueOf(providerName);
        }
        return PriceConfig.builder()
            .enabled(preferences.getBoolean(KEY_ENABLED, false))
            .providerType(providerType)
            .symbols(symbol == null ? List.of() : List.of(symbol))
            .apiKey(preferences.getString(KEY_API_KEY, null))
            .refreshIntervalSeconds(preferences.getInt(KEY_REFRESH_SECONDS, 120))
            .timeoutMillis(preferences.getInt(KEY_TIMEOUT_MILLIS, 3_000))
            .customUrlTemplate(preferences.getString(KEY_CUSTOM_URL, null))
            .customJsonPathPrice(preferences.getString(KEY_CUSTOM_PRICE, null))
            .customJsonPathSymbol(preferences.getString(KEY_CUSTOM_SYMBOL, null))
            .customJsonPathCurrency(preferences.getString(KEY_CUSTOM_CURRENCY, null))
            .customJsonPathTimestamp(preferences.getString(KEY_CUSTOM_TIMESTAMP, null))
            .build();
    }

    @Override
    public void saveConfig(PriceConfig config) {
        if (config == null) {
            preferences().edit().clear().apply();
            return;
        }
        preferences().edit()
            .putBoolean(KEY_ENABLED, config.enabled())
            .putString(KEY_PROVIDER, config.providerType() == null ? null : config.providerType().name())
            .putString(KEY_SYMBOL, config.symbol())
            .putString(KEY_API_KEY, config.apiKey())
            .putInt(KEY_REFRESH_SECONDS, config.refreshPolicy().refreshIntervalSeconds())
            .putInt(KEY_TIMEOUT_MILLIS, config.refreshPolicy().timeoutMillis())
            .putString(KEY_CUSTOM_URL, config.customUrlTemplate())
            .putString(KEY_CUSTOM_PRICE, config.customJsonPathPrice())
            .putString(KEY_CUSTOM_SYMBOL, config.customJsonPathSymbol())
            .putString(KEY_CUSTOM_CURRENCY, config.customJsonPathCurrency())
            .putString(KEY_CUSTOM_TIMESTAMP, config.customJsonPathTimestamp())
            .apply();
    }

    @Override
    public PriceState loadState() {
        SharedPreferences preferences = preferences();
        String errorCode = preferences.getString(KEY_CACHE_ERROR_CODE, null);
        if (errorCode != null) {
            return PriceState.withError(ProviderError.of(
                ProviderError.Code.valueOf(errorCode),
                preferences.getString(KEY_CACHE_ERROR_MESSAGE, "")
            ));
        }
        if (!preferences.getBoolean(KEY_CACHE_HAS_QUOTE, false)) {
            return PriceState.empty();
        }
        PriceQuote quote = new PriceQuote(
            preferences.getString(KEY_CACHE_SYMBOL, ""),
            Double.longBitsToDouble(preferences.getLong(KEY_CACHE_PRICE, Double.doubleToRawLongBits(0D))),
            preferences.getString(KEY_CACHE_CURRENCY, ""),
            preferences.getLong(KEY_CACHE_TIMESTAMP, 0L)
        );
        return PriceState.withQuote(quote, preferences.getLong(KEY_CACHE_FETCHED_AT, 0L));
    }

    @Override
    public void saveState(PriceState state) {
        SharedPreferences.Editor editor = preferences().edit()
            .remove(KEY_CACHE_HAS_QUOTE)
            .remove(KEY_CACHE_SYMBOL)
            .remove(KEY_CACHE_PRICE)
            .remove(KEY_CACHE_CURRENCY)
            .remove(KEY_CACHE_TIMESTAMP)
            .remove(KEY_CACHE_FETCHED_AT)
            .remove(KEY_CACHE_ERROR_CODE)
            .remove(KEY_CACHE_ERROR_MESSAGE);
        if (state == null) {
            editor.apply();
            return;
        }
        if (state.error() != null) {
            editor.putString(KEY_CACHE_ERROR_CODE, state.error().code().name())
                .putString(KEY_CACHE_ERROR_MESSAGE, state.error().message())
                .apply();
            return;
        }
        if (state.hasQuote()) {
            editor.putBoolean(KEY_CACHE_HAS_QUOTE, true)
                .putString(KEY_CACHE_SYMBOL, state.quote().symbol())
                .putLong(KEY_CACHE_PRICE, Double.doubleToRawLongBits(state.quote().price()))
                .putString(KEY_CACHE_CURRENCY, state.quote().currency())
                .putLong(KEY_CACHE_TIMESTAMP, state.quote().timestampMillis())
                .putLong(KEY_CACHE_FETCHED_AT, state.fetchedAtMillis());
        }
        editor.apply();
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
