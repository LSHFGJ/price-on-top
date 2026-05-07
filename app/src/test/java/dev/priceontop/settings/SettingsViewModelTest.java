package dev.priceontop.settings;

import org.junit.Test;
import static org.junit.Assert.*;

import dev.priceontop.core.PriceConfig;
import dev.priceontop.core.ProviderType;
import dev.priceontop.core.PriceState;
import dev.priceontop.storage.PriceStorage;
import java.util.Map;
import java.util.HashMap;

public class SettingsViewModelTest {

    private static class FakeStorage implements PriceStorage {
        PriceConfig savedConfig = null;
        
        @Override public PriceConfig loadConfig() { return savedConfig; }
        @Override public void saveConfig(PriceConfig config) { this.savedConfig = config; }
        @Override public PriceState loadState() { return null; }
        @Override public void saveState(PriceState state) {}
    }

    @Test
    public void saveValidFinnhubConfig() {
        FakeStorage storage = new FakeStorage();
        SettingsViewModel viewModel = new SettingsViewModel(storage);

        viewModel.setEnabled(true);
        viewModel.setProvider("FINNHUB");
        viewModel.setSymbol("AAPL");
        viewModel.setApiKey("test-key");
        viewModel.setRefreshInterval("120");

        assertTrue(viewModel.save());
        
        PriceConfig saved = storage.savedConfig;
        assertNotNull(saved);
        assertTrue(saved.enabled());
        assertEquals(ProviderType.FINNHUB, saved.providerType());
        assertEquals("AAPL", saved.symbol());
        assertEquals("test-key", saved.apiKey());
        assertEquals(120, saved.refreshPolicy().refreshIntervalSeconds());
    }

    @Test
    public void rejectsInvalidRefreshInterval() {
        FakeStorage storage = new FakeStorage();
        SettingsViewModel viewModel = new SettingsViewModel(storage);

        viewModel.setEnabled(true);
        viewModel.setProvider("FINNHUB");
        viewModel.setSymbol("AAPL");
        viewModel.setRefreshInterval("-1");

        assertFalse(viewModel.save());
        assertNull(storage.savedConfig);
        assertTrue(viewModel.getErrors().contains("refresh interval must be positive"));
    }

    @Test
    public void persistsCustomJsonConfiguration() {
        FakeStorage storage = new FakeStorage();
        SettingsViewModel viewModel = new SettingsViewModel(storage);

        viewModel.setEnabled(true);
        viewModel.setProvider("CUSTOM_JSON");
        viewModel.setSymbol("ETH");
        viewModel.setCustomUrlTemplate("https://api.example.com/eth");
        viewModel.setCustomJsonPathPrice("$.price");
        viewModel.setCustomJsonPathSymbol("$.symbol");
        viewModel.setCustomJsonPathCurrency("$.currency");
        viewModel.setCustomJsonPathTimestamp("$.timestamp");

        assertTrue(viewModel.getErrors().toString(), viewModel.save());

        PriceConfig saved = storage.savedConfig;
        assertNotNull(saved);
        assertEquals(ProviderType.CUSTOM_JSON, saved.providerType());
        assertEquals("https://api.example.com/eth", saved.customUrlTemplate());
        assertEquals("$.price", saved.customJsonPathPrice());
        assertEquals("$.symbol", saved.customJsonPathSymbol());
        assertEquals("$.currency", saved.customJsonPathCurrency());
        assertEquals("$.timestamp", saved.customJsonPathTimestamp());
    }

    @Test
    public void rejectsCustomProviderWithoutUrlTemplate() {
        FakeStorage storage = new FakeStorage();
        SettingsViewModel viewModel = new SettingsViewModel(storage);

        viewModel.setEnabled(true);
        viewModel.setProvider("CUSTOM_JSON");
        viewModel.setSymbol("BTC");
        viewModel.setCustomUrlTemplate("");
        viewModel.setCustomJsonPathPrice("$.price");

        assertFalse(viewModel.save());
        assertNull(storage.savedConfig);
        assertTrue(viewModel.getErrors().contains("custom URL template is required for CUSTOM_JSON"));
    }

    @Test
    public void rejectsCustomProviderWithoutPriceJsonPath() {
        FakeStorage storage = new FakeStorage();
        SettingsViewModel viewModel = new SettingsViewModel(storage);

        viewModel.setEnabled(true);
        viewModel.setProvider("CUSTOM_JSON");
        viewModel.setSymbol("BTC");
        viewModel.setCustomUrlTemplate("https://api.example.com/price");
        viewModel.setCustomJsonPathPrice("");

        assertFalse(viewModel.save());
        assertNull(storage.savedConfig);
        assertTrue(viewModel.getErrors().contains("price JSONPath is required for CUSTOM_JSON"));
    }

    @Test
    public void loadingConfigWithApiKeyReturnsMaskedDisplayValue() {
        FakeStorage storage = new FakeStorage();
        PriceConfig config = PriceConfig.builder()
            .enabled(true)
            .providerType(ProviderType.FINNHUB)
            .symbols(java.util.List.of("AAPL"))
            .apiKey("my-secret-key")
            .refreshIntervalSeconds(60)
            .timeoutMillis(2000)
            .build();
        storage.saveConfig(config);

        SettingsViewModel viewModel = new SettingsViewModel(storage);
        assertEquals("***", viewModel.getDisplayApiKey());
        assertEquals("my-secret-key", viewModel.getApiKey());
    }

    @Test
    public void savingWithRawKeyMasksDisplayButRetainsRaw() {
        FakeStorage storage = new FakeStorage();
        SettingsViewModel viewModel = new SettingsViewModel(storage);

        viewModel.setProvider("FINNHUB");
        viewModel.setSymbol("AAPL");
        viewModel.setApiKey("new-secret");

        assertTrue(viewModel.save());
        assertEquals("***", viewModel.getDisplayApiKey());
        assertEquals("new-secret", viewModel.getApiKey());
        assertEquals("new-secret", storage.savedConfig.apiKey());
    }

    @Test
    public void settingApiKeyToMaskPreservesExistingStoredKey() {
        FakeStorage storage = new FakeStorage();
        PriceConfig config = PriceConfig.builder()
            .enabled(true)
            .providerType(ProviderType.FINNHUB)
            .symbols(java.util.List.of("AAPL"))
            .apiKey("existing-secret")
            .refreshIntervalSeconds(60)
            .timeoutMillis(2000)
            .build();
        storage.saveConfig(config);

        SettingsViewModel viewModel = new SettingsViewModel(storage);
        viewModel.setApiKey("***");

        assertTrue(viewModel.save());
        assertEquals("existing-secret", viewModel.getApiKey());
        assertEquals("existing-secret", storage.savedConfig.apiKey());
        assertEquals("***", viewModel.getDisplayApiKey());
    }

    @Test
    public void clearingApiKeyRemovesIt() {
        FakeStorage storage = new FakeStorage();
        PriceConfig config = PriceConfig.builder()
            .enabled(true)
            .providerType(ProviderType.FINNHUB)
            .symbols(java.util.List.of("AAPL"))
            .apiKey("existing-secret")
            .refreshIntervalSeconds(60)
            .timeoutMillis(2000)
            .build();
        storage.saveConfig(config);

        SettingsViewModel viewModel = new SettingsViewModel(storage);
        viewModel.setApiKey("");

        assertTrue(viewModel.save());
        assertEquals("", viewModel.getApiKey());
        assertNull(storage.savedConfig.apiKey());
        assertEquals("", viewModel.getDisplayApiKey());
    }

    @Test
    public void testProviderUpdatesLastSuccessfulPreviewOnlyOnSuccess() {
        FakeStorage storage = new FakeStorage();
        SettingsViewModel viewModel = new SettingsViewModel(storage);
        
        assertEquals("", viewModel.getLastSuccessfulPreview());

        viewModel.setProvider("FINNHUB");
        viewModel.setSymbol("");
        viewModel.setApiKey("preview-key");
        String errorPreview = viewModel.testProvider();
        assertTrue(errorPreview.contains("invalid"));
        assertEquals("", viewModel.getLastSuccessfulPreview());

        viewModel.setSymbol("AAPL");
        String successPreview = viewModel.testProvider();
        assertTrue(successPreview.contains("Preview:"));
        assertEquals(successPreview, viewModel.getLastSuccessfulPreview());

        viewModel.setSymbol("");
        String errorPreview2 = viewModel.testProvider();
        assertTrue(errorPreview2.contains("invalid"));
        assertEquals(successPreview, viewModel.getLastSuccessfulPreview());
    }
    @Test
    public void testProviderProducesFormattedPreview() {
        FakeStorage storage = new FakeStorage();
        SettingsViewModel viewModel = new SettingsViewModel(storage);

        viewModel.setProvider("FINNHUB");
        viewModel.setSymbol("AAPL");
        viewModel.setApiKey("super-secret-key");

        String preview = viewModel.testProvider();
        assertFalse(preview.contains("PriceConfig{"));
        assertFalse(preview.contains("super-secret-key"));
        assertTrue(preview.contains("***"));
        assertTrue("Expected '123' or similar in " + preview, preview.contains("123"));
    }
}
