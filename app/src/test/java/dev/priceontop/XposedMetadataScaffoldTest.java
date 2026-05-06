package dev.priceontop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.Test;

public final class XposedMetadataScaffoldTest {
    private static final Path METADATA_DIR = Path.of("src", "main", "resources", "META-INF", "xposed");

    @Test
    public void declaresModernApi101MetadataForSystemUiOnlyScope() throws IOException {
        Path moduleProp = METADATA_DIR.resolve("module.prop");
        Path scopeList = METADATA_DIR.resolve("scope.list");
        Path javaInitList = METADATA_DIR.resolve("java_init.list");

        assertTrue("module.prop must exist", Files.isRegularFile(moduleProp));
        assertTrue("scope.list must exist", Files.isRegularFile(scopeList));
        assertTrue("java_init.list must exist", Files.isRegularFile(javaInitList));

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(moduleProp)) {
            properties.load(input);
        }

        assertEquals("101", properties.getProperty("minApiVersion"));
        assertEquals("101", properties.getProperty("targetApiVersion"));
        assertEquals(List.of("com.android.systemui"), normalizedLines(scopeList));
        assertEquals(List.of("dev.priceontop.xposed.PriceOnTopModule"), normalizedLines(javaInitList));
    }

    @Test
    public void omitsLegacyXposedMetadata() throws IOException {
        assertFalse(Files.exists(Path.of("src", "main", "assets", "xposed_init")));

        Path manifest = Path.of("src", "main", "AndroidManifest.xml");
        String manifestText = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8).toLowerCase();

        assertFalse(manifestText.contains("xposed" + "minversion"));
        assertFalse(manifestText.contains("xposed" + "module"));
        assertFalse(manifestText.contains("xposed" + "description"));
    }

    private static List<String> normalizedLines(Path path) throws IOException {
        return Files.readAllLines(path).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .filter(line -> !line.startsWith("#"))
            .collect(Collectors.toList());
    }
}
