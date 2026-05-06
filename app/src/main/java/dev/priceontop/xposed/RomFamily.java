package dev.priceontop.xposed;

import java.util.Locale;

public enum RomFamily {
    AOSP_PIXEL_LINEAGE,
    MIUI_HYPEROS,
    UNKNOWN;

    public static RomFamily detect(String fingerprint, String manufacturer, String brand) {
        try {
            String searchable = normalize(fingerprint) + ' ' + normalize(manufacturer) + ' ' + normalize(brand);
            if (containsAny(searchable, "hyperos", "miui", "xiaomi", "redmi", "poco")) {
                return MIUI_HYPEROS;
            }
            if (containsAny(searchable, "lineage", "aosp", "pixel", "google")) {
                return AOSP_PIXEL_LINEAGE;
            }
            return UNKNOWN;
        } catch (RuntimeException exception) {
            return UNKNOWN;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
