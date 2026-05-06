package dev.priceontop.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class ConfigValidationTest {
    private static final String DUMMY_RAW_SECRET = "DUMMY_RAW_SECRET_VALUE";

    @Test
    public void validFinnhubConfigAcceptsSingleAaplSymbolWithDefaultPolicy() throws Exception {
        Object config = buildConfig(true, "FINNHUB", List.of("AAPL"), DUMMY_RAW_SECRET, 120, 3000);
        Object validation = invoke(config, "validate");

        assertTrue("FINNHUB AAPL config should be valid", (Boolean) invoke(validation, "isValid"));
        assertEquals("AAPL", invoke(config, "symbol"));
        assertEquals(120, invoke(invoke(config, "refreshPolicy"), "refreshIntervalSeconds"));
        assertEquals(3000, invoke(invoke(config, "refreshPolicy"), "timeoutMillis"));
        assertFalse(String.valueOf(config).contains(DUMMY_RAW_SECRET));
        assertSanitizedMap(config);
    }

    @Test
    public void exposesCoreDomainContractTypes() throws Exception {
        assertContractExists("dev.priceontop.core.PriceConfig");
        assertContractExists("dev.priceontop.core.ProviderType");
        assertContractExists("dev.priceontop.core.PriceQuote");
        assertContractExists("dev.priceontop.core.PriceState");
        assertContractExists("dev.priceontop.core.RefreshPolicy");
        assertContractExists("dev.priceontop.core.DisplayFormat");
        assertContractExists("dev.priceontop.core.ProviderError");
    }

    @Test
    public void rejectsBlankOrMultipleSymbols() throws Exception {
        Object blank = buildConfig(true, "FINNHUB", List.of(" "), DUMMY_RAW_SECRET, 120, 3000);
        Object multiple = buildConfig(true, "FINNHUB", Arrays.asList("AAPL", "MSFT"), DUMMY_RAW_SECRET, 120, 3000);

        assertInvalidWithMessage(blank, "exactly one nonblank symbol");
        assertInvalidWithMessage(multiple, "exactly one nonblank symbol");
    }

    @Test
    public void validatesNullSymbolAsInvalidInsteadOfCrashing() throws Exception {
        List<String> symbols = new ArrayList<>();
        symbols.add(null);
        Object config = buildConfig(true, "FINNHUB", symbols, DUMMY_RAW_SECRET, 120, 3000);

        assertInvalidWithMessage(config, "exactly one nonblank symbol");
    }

    @Test
    public void rejectsMissingProviderSelection() throws Exception {
        Object config = buildConfig(true, null, List.of("AAPL"), DUMMY_RAW_SECRET, 120, 3000);

        assertInvalidWithMessage(config, "provider is required");
    }

    @Test
    public void rejectsRefreshIntervalOutsideAllowedBounds() throws Exception {
        Object tooFast = buildConfig(true, "FINNHUB", List.of("AAPL"), DUMMY_RAW_SECRET, 59, 3000);
        Object tooSlow = buildConfig(true, "FINNHUB", List.of("AAPL"), DUMMY_RAW_SECRET, 901, 3000);

        assertInvalidWithMessage(tooFast, "refresh interval must be between 60 and 900 seconds");
        assertInvalidWithMessage(tooSlow, "refresh interval must be between 60 and 900 seconds");
    }

    @Test
    public void rejectsTimeoutAboveMaximum() throws Exception {
        Object config = buildConfig(true, "FINNHUB", List.of("AAPL"), DUMMY_RAW_SECRET, 120, 5001);

        assertInvalidWithMessage(config, "timeout must be at most 5000 milliseconds");
    }

    @Test
    public void defaultsRefreshTimeoutAndStalenessThresholds() throws Exception {
        Object config = buildConfig(true, "FINNHUB", List.of("AAPL"), DUMMY_RAW_SECRET, null, null);
        Object policy = invoke(config, "refreshPolicy");

        assertTrue((Boolean) invoke(invoke(config, "validate"), "isValid"));
        assertEquals(120, invoke(policy, "refreshIntervalSeconds"));
        assertEquals(3000, invoke(policy, "timeoutMillis"));
        assertEquals(300_000L, invoke(policy, "staleThresholdMillis"));
        assertEquals(1_800_000L, invoke(policy, "hideThresholdMillis"));
    }

    @Test
    public void redactsKeysTokensAndSecretsInDiagnostics() throws Exception {
        Class<?> redactor = assertContractExists("dev.priceontop.core.DiagnosticsRedactor");
        Method redact = redactor.getMethod("redact", String.class);
        String diagnostic = "apiKey=" + DUMMY_RAW_SECRET + " token=" + DUMMY_RAW_SECRET + " secret:" + DUMMY_RAW_SECRET;

        String sanitized = (String) redact.invoke(null, diagnostic);

        assertFalse(sanitized.contains(DUMMY_RAW_SECRET));
        assertTrue(sanitized.contains("apiKey=***"));
        assertTrue(sanitized.contains("token=***"));
        assertTrue(sanitized.contains("secret:***"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void redactsFreeTextProviderErrorMessagesInSanitizedState() throws Exception {
        Class<?> providerErrorClass = assertContractExists("dev.priceontop.core.ProviderError");
        Class<?> codeClass = assertContractExists("dev.priceontop.core.ProviderError$Code");
        Object invalidResponse = Enum.valueOf((Class<Enum>) codeClass.asSubclass(Enum.class), "INVALID_RESPONSE");
        Object error = providerErrorClass
            .getMethod("of", codeClass, String.class)
            .invoke(null, invalidResponse, "upstream echoed key " + DUMMY_RAW_SECRET);

        Class<?> priceStateClass = assertContractExists("dev.priceontop.core.PriceState");
        Object state = priceStateClass.getMethod("withError", providerErrorClass).invoke(null, error);
        Map<String, String> sanitized = (Map<String, String>) invoke(state, "toSanitizedMap");

        assertFalse(sanitized.toString().contains(DUMMY_RAW_SECRET));
        assertEquals("Provider error redacted", sanitized.get("errorMessage"));
    }

    @SuppressWarnings("unchecked")
    private static void assertSanitizedMap(Object config) throws Exception {
        Map<String, String> sanitized = (Map<String, String>) invoke(config, "toSanitizedMap");
        assertFalse(sanitized.toString().contains(DUMMY_RAW_SECRET));
        assertEquals("***", sanitized.get("apiKey"));
    }

    private static void assertInvalidWithMessage(Object config, String expectedMessage) throws Exception {
        Object validation = invoke(config, "validate");
        assertFalse("config should be invalid", (Boolean) invoke(validation, "isValid"));
        assertTrue(String.valueOf(invoke(validation, "errors")).contains(expectedMessage));
        assertFalse(String.valueOf(validation).contains(DUMMY_RAW_SECRET));
    }

    private static Object buildConfig(
        boolean enabled,
        String providerName,
        List<String> symbols,
        String apiKey,
        Integer refreshIntervalSeconds,
        Integer timeoutMillis
    ) throws Exception {
        Class<?> configClass = assertContractExists("dev.priceontop.core.PriceConfig");
        Object builder = invokeStatic(configClass, "builder");
        invoke(builder, "enabled", new Class<?>[] {boolean.class}, enabled);
        if (providerName != null) {
            Class<?> providerTypeClass = assertContractExists("dev.priceontop.core.ProviderType");
            Object providerType = Enum.valueOf((Class<Enum>) providerTypeClass.asSubclass(Enum.class), providerName);
            invoke(builder, "providerType", new Class<?>[] {providerTypeClass}, providerType);
        }
        invoke(builder, "symbols", new Class<?>[] {List.class}, symbols);
        invoke(builder, "apiKey", new Class<?>[] {String.class}, apiKey);
        if (refreshIntervalSeconds != null) {
            invoke(builder, "refreshIntervalSeconds", new Class<?>[] {Integer.class}, refreshIntervalSeconds);
        }
        if (timeoutMillis != null) {
            invoke(builder, "timeoutMillis", new Class<?>[] {Integer.class}, timeoutMillis);
        }
        return invoke(builder, "build");
    }

    private static Class<?> assertContractExists(String className) throws Exception {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            fail("Missing core contract: " + className);
            throw exception;
        }
    }

    private static Object invokeStatic(Class<?> target, String methodName) throws Exception {
        Method method = target.getMethod(methodName);
        return method.invoke(null);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }
}
