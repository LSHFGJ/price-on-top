package dev.priceontop.storage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.ProviderType;
import org.junit.Test;

public final class PriceTopContractTest {
    @Test
    public void shouldRegisterSystemUiHooksUsesPureGateLogic() {
        PriceConfig config = configWithEnabled(true);

        assertFalse(SystemUiHookGate.shouldRegisterSystemUiHooks(config, true));
        assertTrue(SystemUiHookGate.shouldRegisterSystemUiHooks(config, false));
        assertFalse(SystemUiHookGate.shouldRegisterSystemUiHooks(null, true));
        assertFalse(SystemUiHookGate.shouldRegisterSystemUiHooks(null, false));
    }

    @Test
    public void shouldRegisterSystemUiHooksFailsClosedWhenConfigDisabled() {
        PriceConfig disabledConfig = configWithEnabled(false);

        assertFalse(SystemUiHookGate.shouldRegisterSystemUiHooks(disabledConfig, false));
        assertFalse(PriceTopContract.shouldRegisterSystemUiHooks(disabledConfig, false));
    }

    @Test
    public void shouldRegisterSystemUiHooksFailsClosedWhenKillSwitchEnabled() {
        PriceConfig enabledConfig = configWithEnabled(true);

        assertFalse(SystemUiHookGate.shouldRegisterSystemUiHooks(enabledConfig, true));
        assertFalse(PriceTopContract.shouldRegisterSystemUiHooks(enabledConfig, true));
    }

    private static PriceConfig configWithEnabled(boolean enabled) {
        return PriceConfig.builder()
            .enabled(enabled)
            .providerType(ProviderType.FINNHUB)
            .build();
    }
}
