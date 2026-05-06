package dev.priceontop.xposed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import org.junit.Test;

public final class XposedHookDiagnosticsTest {
    @Test
    public void redactsApiKeyTokenSecretAndBearerValues() throws Exception {
        Class<?> diagnosticsClass = assertClassExists("dev.priceontop.xposed.XposedHookDiagnostics");
        Method redact = diagnosticsClass.getMethod("redact", String.class);
        String diagnostic = "hook failed apiKey=LIVE_API_KEY token=LIVE_TOKEN secret:LIVE_SECRET "
            + "Authorization:LIVE_AUTH Bearer LIVE_BEARER";

        String redacted = (String) redact.invoke(null, diagnostic);

        assertFalse(redacted.contains("LIVE_API_KEY"));
        assertFalse(redacted.contains("LIVE_TOKEN"));
        assertFalse(redacted.contains("LIVE_SECRET"));
        assertFalse(redacted.contains("LIVE_AUTH"));
        assertFalse(redacted.contains("LIVE_BEARER"));
        assertTrue(redacted.contains("apiKey=***"));
        assertTrue(redacted.contains("token=***"));
        assertTrue(redacted.contains("secret:***"));
        assertTrue(redacted.contains("Authorization:***"));
        assertTrue(redacted.contains("Bearer ***"));
    }

    private static Class<?> assertClassExists(String className) throws Exception {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            fail("Missing xposed diagnostics class: " + className);
            throw exception;
        }
    }
}
