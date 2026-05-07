package dev.priceontop.storage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.ProviderType;
import org.junit.Test;

public final class PriceTopContractTest {
    @Test
    public void shouldRegisterSystemUiHooksUsesPureGateLogic() {
        PriceConfig config = PriceConfig.builder()
            .enabled(true)
            .providerType(ProviderType.FINNHUB)
            .build();

        assertFalse(SystemUiHookGate.shouldRegisterSystemUiHooks(config, true));
        assertTrue(SystemUiHookGate.shouldRegisterSystemUiHooks(config, false));
        assertFalse(SystemUiHookGate.shouldRegisterSystemUiHooks(null, true));
        assertFalse(SystemUiHookGate.shouldRegisterSystemUiHooks(null, false));
    }
}
