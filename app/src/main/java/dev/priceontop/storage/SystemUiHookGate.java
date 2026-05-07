package dev.priceontop.storage;

import dev.priceontop.core.PriceConfig;

public final class SystemUiHookGate {
    private SystemUiHookGate() {
    }

    public static boolean shouldRegisterSystemUiHooks(PriceConfig config, boolean systemUiHookKillSwitchEnabled) {
        return config != null && config.enabled() && !systemUiHookKillSwitchEnabled;
    }
}
