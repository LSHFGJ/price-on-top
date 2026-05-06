package dev.priceontop.storage;

import android.net.Uri;

public final class PriceTopContract {
    public static final String AUTHORITY = "dev.priceontop.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static final String METHOD_GET_CONFIG = "get_config";
    public static final String METHOD_GET_CACHE = "get_cache";
    public static final String METHOD_SAVE_CONFIG = "save_config";

    public static final String KEY_STATUS = "status";
    public static final String KEY_ALLOWED = "allowed";
    public static final String KEY_PREFIX_CONFIG = "config.";
    public static final String KEY_PREFIX_CACHE = "cache.";

    private PriceTopContract() {
    }
}
