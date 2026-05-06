package dev.priceontop.storage;

import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.PriceState;

public interface PriceStorage {
    PriceConfig loadConfig();

    void saveConfig(PriceConfig config);

    PriceState loadState();

    void saveState(PriceState state);
}
