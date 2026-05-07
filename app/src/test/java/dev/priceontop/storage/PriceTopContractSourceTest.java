package dev.priceontop.storage;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class PriceTopContractSourceTest {
    @Test
    public void contractDefinesSystemUiDefaultsAndGate() throws IOException {
        String source = readSource("PriceTopContract.java");

        assertTrue(source.contains("KEY_SYSTEM_UI_HOOK_KILL_SWITCH"));
        assertTrue(source.contains("KEY_EXPERIMENTAL_PLACEMENT_ENABLED"));
        assertTrue(source.contains("putSystemUiDefaults(Bundle bundle, boolean experimentalPlacementEnabled)"));
        assertTrue(source.contains("shouldRegisterSystemUiHooks(Bundle bundle)"));
        assertTrue(source.contains("bundle.putString(KEY_PREFIX_CONFIG + KEY_SYSTEM_UI_HOOK_KILL_SWITCH, Boolean.toString(false));"));
        assertTrue(source.contains("bundle.putString(KEY_PREFIX_CONFIG + KEY_EXPERIMENTAL_PLACEMENT_ENABLED, Boolean.toString(experimentalPlacementEnabled));"));
        assertTrue(source.contains("return shouldRegisterSystemUiHooks(configFromBundle(bundle), systemUiHookKillSwitchEnabled(bundle));"));
        assertTrue(source.contains("return SystemUiHookGate.shouldRegisterSystemUiHooks(config, systemUiHookKillSwitchEnabled);"));
        assertTrue(source.contains("shouldRegisterSystemUiHooks(PriceConfig config, boolean systemUiHookKillSwitchEnabled)"));
        assertTrue(source.contains("systemUiHookKillSwitchEnabled(bundle)"));
        assertTrue(source.contains("hasSystemUiConfig(Bundle bundle)"));
        assertTrue(source.contains("experimentalPlacementEnabled(Bundle bundle)"));
    }

    @Test
    public void providerBacksSystemUiKillSwitchWithConfigDefaults() throws IOException {
        String contractSource = readSource("PriceTopContract.java");
        String providerSource = readSource("PriceTopProvider.java");

        assertTrue(contractSource.contains("KEY_PREFIX_CONFIG + KEY_SYSTEM_UI_HOOK_KILL_SWITCH"));
        assertTrue(contractSource.contains("bundle.putString(KEY_PREFIX_CONFIG + KEY_SYSTEM_UI_HOOK_KILL_SWITCH, Boolean.toString(false));"));
        assertTrue(contractSource.contains("systemUiHookKillSwitchEnabled(bundle)"));
        assertTrue(providerSource.contains("PriceTopContract.putSystemUiDefaults(bundle, false);"));
    }

    private static String readSource(String fileName) throws IOException {
        Path sourcePath = Path.of("src", "main", "java", "dev", "priceontop", "storage", fileName);
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }
}
