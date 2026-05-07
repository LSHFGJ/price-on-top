package dev.priceontop.xposed;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public final class NoNetworkOnUiThreadGuardTest {
    private static final List<String> FORBIDDEN_DIRECT_NETWORK_TOKENS = List.of(
        "HttpURLConnection",
        "openConnection(",
        "HttpTransport",
        "FinnhubProvider",
        "CustomJsonProvider",
        ".fetch(",
        "new Thread(",
        "Executors."
    );

    @Test
    public void hookCallbackAndClockMutationClassesContainNoDirectNetworkCalls() throws IOException {
        assertNoDirectNetworkTokens(Path.of("src", "main", "java", "dev", "priceontop", "xposed", "PriceOnTopModule.java"));
        assertNoDirectNetworkTokens(Path.of("src", "main", "java", "dev", "priceontop", "xposed", "SystemUiPriceController.java"));
        assertNoDirectNetworkTokens(Path.of("src", "main", "java", "dev", "priceontop", "xposed", "ClockTextDecorator.java"));
        assertNoDirectNetworkTokens(Path.of("src", "main", "java", "dev", "priceontop", "xposed", "adapter", "ClockTargetAdapter.java"));
        assertNoDirectNetworkTokens(Path.of("src", "main", "java", "dev", "priceontop", "xposed", "adapter", "AospClockAdapter.java"));
        assertNoDirectNetworkTokens(Path.of("src", "main", "java", "dev", "priceontop", "xposed", "adapter", "MiuiHyperOsClockAdapter.java"));
    }

    private static void assertNoDirectNetworkTokens(Path sourcePath) throws IOException {
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        for (String token : FORBIDDEN_DIRECT_NETWORK_TOKENS) {
            assertFalse(sourcePath + " must not contain " + token, source.contains(token));
        }
    }
}
