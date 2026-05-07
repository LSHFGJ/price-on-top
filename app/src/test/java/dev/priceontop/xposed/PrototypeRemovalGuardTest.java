package dev.priceontop.xposed;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class PrototypeRemovalGuardTest {
    private static final List<String> FORBIDDEN_PROTOTYPE_TOKENS = List.of(
        "StatusBarPriceAreaAdapter",
        "systemui-price-area-hook-installed",
        "controller rendered price area",
        "NotificationIconContainer",
        "StatusIconContainer",
        "PhoneStatusBarView",
        ".addView("
    );

    @Test
    public void runtimeSourceAndSmokeScriptContainNoPrototypeMarkers() throws IOException {
        List<String> offenders = new ArrayList<>();

        assertNoPrototypeTokens(Path.of("src", "main", "java", "dev", "priceontop", "xposed"), offenders);
        assertNoPrototypeTokens(Path.of("..", "scripts", "smoke-rooted.sh"), offenders);

        assertTrue("No prototype markers expected. Found:\n" + String.join("\n", offenders), offenders.isEmpty());
    }

    private static void assertNoPrototypeTokens(Path targetPath, List<String> offenders) throws IOException {
        if (!Files.exists(targetPath)) {
            offenders.add(targetPath + " must exist");
            return;
        }
        if (Files.isDirectory(targetPath)) {
            try (var files = Files.walk(targetPath)) {
                files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> scanFileForForbiddenTokens(path, offenders));
            }
        } else {
            scanFileForForbiddenTokens(targetPath, offenders);
        }
    }

    private static void scanFileForForbiddenTokens(Path sourcePath, List<String> offenders) {
        try {
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            for (String token : FORBIDDEN_PROTOTYPE_TOKENS) {
                if (source.contains(token)) {
                    offenders.add(sourcePath + " contains forbidden token: " + token);
                }
            }
        } catch (IOException exception) {
            offenders.add(sourcePath + " could not be read: " + exception.getMessage());
        }
    }
}
