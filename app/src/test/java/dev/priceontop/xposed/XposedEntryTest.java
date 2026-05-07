package dev.priceontop.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class XposedEntryTest {
    @Test
    public void keepsModernEntrySmallAndGuarded() throws IOException {
        String source = readSource("PriceOnTopModule.java");

        assertTrue(source.contains("extends XposedModule"));
        assertTrue(source.contains("onModuleLoaded"));
        assertTrue(source.contains("onPackageLoaded"));
        assertTrue(source.contains("XposedScopeGuard.shouldRegister"));
        assertTrue(source.contains("HookFailurePolicy"));
        assertFalse(source.contains("IXposedHookLoadPackage"));
        assertFalse(source.contains("assets/xposed_init"));
        assertEquals("dev.priceontop.xposed.PriceOnTopModule", readMetadata("java_init.list"));
        assertEquals("com.android.systemui", readMetadata("scope.list"));
    }

    @Test
    public void contextFallbackKeepsSystemContextWhenApplicationContextUnavailable() throws IOException {
        String source = readSource("PriceOnTopModule.java");

        assertTrue(source.contains("Context applicationContext = systemContext.getApplicationContext();"));
        assertTrue(source.contains("return applicationContext == null ? systemContext : applicationContext;"));
    }

    @Test
    public void moduleSkipsSystemUiHookRegistrationWhenContractDisables() throws IOException {
        String source = readSource("PriceOnTopModule.java");
        String body = between(source, "private void continueForSystemUi", "private boolean shouldRegisterSystemUiHooks");

        assertTrue(body.contains("if (!shouldRegisterSystemUiHooks(packageName, processName, romFamily))"));
        assertTrue(body.contains("systemui-hook-registration-skipped"));
        assertTrue(body.contains("registerClockHooks(param, romFamily, activeController);"));
    }

    @Test
    public void moduleHookRegistrationUsesContractAndKillSwitch() throws IOException {
        String source = readSource("PriceOnTopModule.java");
        String guardBody = between(source, "private boolean shouldRegisterSystemUiHooks", "private Bundle configBundleFromProvider");

        assertTrue(source.contains("configBundleFromProvider()"));
        assertTrue(guardBody.contains("PriceTopContract.hasSystemUiConfig(configBundle)"));
        assertTrue(guardBody.contains("PriceTopContract.systemUiHookKillSwitchEnabled(configBundle)"));
        assertTrue(guardBody.contains("PriceTopContract.experimentalPlacementEnabled(configBundle)"));
    }

    @Test
    public void moduleUsesContractGateForSystemUiHookDecision() throws IOException {
        String source = readSource("PriceOnTopModule.java");

        assertTrue(source.contains("boolean shouldRegisterSystemUiHooks(String packageName, String processName, RomFamily romFamily)"));
        assertTrue(source.contains("shouldRegisterSystemUiHooks(packageName, processName, romFamily, configBundleFromProvider())"));
        assertTrue(source.contains("PriceTopContract.hasSystemUiConfig(configBundle)"));
        assertTrue(source.contains("PriceTopContract.systemUiHookKillSwitchEnabled(configBundle)"));
    }

    @Test
    public void configBundleFromProviderFailsClosedWhenContextMissing() throws IOException {
        String source = readSource("PriceOnTopModule.java");
        String providerBody = between(source, "private Bundle configBundleFromProvider", "private SystemUiPriceController controller");
        String guardBody = between(source, "boolean shouldRegisterSystemUiHooks", "private Bundle configBundleFromProvider");

        assertTrue(providerBody.contains("Context context = currentProcessContext();"));
        assertTrue(providerBody.contains("if (context == null)"));
        assertTrue(providerBody.contains("ContentResolver resolver = context.getContentResolver();"));
        assertTrue(providerBody.contains("if (resolver == null)"));
        assertTrue(providerBody.contains("return null;"));
        assertTrue(guardBody.contains("PriceTopContract.hasSystemUiConfig(configBundle)"));
        assertTrue(guardBody.contains("return false;"));
    }

    @Test
    public void missingDefaultClassLoaderSkipsClockHookRegistration() throws IOException {
        String source = readSource("PriceOnTopModule.java");
        String hookBody = between(source, "private void registerClockHooks", "private void hookSetTextIfPresent");

        assertTrue(hookBody.contains("ClassLoader classLoader = param == null ? null : param.getDefaultClassLoader();"));
        assertTrue(hookBody.contains("if (classLoader == null)"));
        assertTrue(hookBody.contains("systemui-clock-hook-skipped missing-classloader"));
        assertTrue(hookBody.contains("return;"));
        assertTrue(hookBody.contains("hookSetTextIfPresent(classLoader, className, romFamily, activeController);"));
    }

    private static String readSource(String fileName) throws IOException {
        Path sourcePath = Path.of("src", "main", "java", "dev", "priceontop", "xposed", fileName);
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static String readMetadata(String fileName) throws IOException {
        Path metadataPath = Path.of("src", "main", "resources", "META-INF", "xposed", fileName);
        return new String(Files.readAllBytes(metadataPath), StandardCharsets.UTF_8).trim();
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker);
        return start < 0 || end < 0 || end <= start ? source : source.substring(start, end);
    }
}
