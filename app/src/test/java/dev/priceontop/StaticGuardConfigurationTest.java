package dev.priceontop;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class StaticGuardConfigurationTest {
    @Test
    public void declaresNoSecretsAndNoUiThreadNetworkGuardTask() throws IOException {
        String buildGradle = new String(Files.readAllBytes(Path.of("build.gradle.kts")), StandardCharsets.UTF_8);

        assertTrue(buildGradle.contains("verifyNoSecretsAndNoUiThreadNetwork"));
        assertTrue(buildGradle.contains("query1.finance."));
        assertTrue(buildGradle.contains("yahoo"));
        assertTrue(buildGradle.contains("PriceOnTopModule.java"));
        assertTrue(buildGradle.contains("HttpURLConnection"));
        assertTrue(buildGradle.contains("src/test"));
        assertTrue(buildGradle.contains("settings.gradle.kts"));
        assertTrue(buildGradle.contains("gradle.properties"));
    }
}
