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

    private static String readSource(String fileName) throws IOException {
        Path sourcePath = Path.of("src", "main", "java", "dev", "priceontop", "xposed", fileName);
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

    private static String readMetadata(String fileName) throws IOException {
        Path metadataPath = Path.of("src", "main", "resources", "META-INF", "xposed", fileName);
        return new String(Files.readAllBytes(metadataPath), StandardCharsets.UTF_8).trim();
    }
}
