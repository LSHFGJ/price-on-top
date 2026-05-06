package dev.priceontop.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class HookFailurePolicyTest {
    @Test
    public void disablesAfterRepeatedFailures() throws Exception {
        Class<?> policyClass = assertClassExists("dev.priceontop.xposed.HookFailurePolicy");
        Class<?> attemptClass = assertClassExists("dev.priceontop.xposed.HookFailurePolicy$HookAttempt");
        Object policy = policyClass.getMethod("withMaxFailures", int.class).invoke(null, 2);
        Method runSafely = policyClass.getMethod("runSafely", attemptClass);
        Method isDisabled = policyClass.getMethod("isDisabled");
        Method lastFailureDiagnostic = policyClass.getMethod("lastFailureDiagnostic");
        AtomicInteger attempts = new AtomicInteger();
        Object failingAttempt = Proxy.newProxyInstance(
            attemptClass.getClassLoader(),
            new Class<?>[] {attemptClass},
            (proxy, method, args) -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("boom apiKey=LIVE_HOOK_KEY");
            }
        );

        assertFalse((Boolean) runSafely.invoke(policy, failingAttempt));
        assertFalse((Boolean) isDisabled.invoke(policy));
        assertEquals(1, attempts.get());

        assertFalse((Boolean) runSafely.invoke(policy, failingAttempt));
        assertTrue((Boolean) isDisabled.invoke(policy));
        assertEquals(2, attempts.get());

        assertFalse((Boolean) runSafely.invoke(policy, failingAttempt));
        assertTrue((Boolean) isDisabled.invoke(policy));
        assertEquals("disabled policy must skip further hook attempts", 2, attempts.get());

        String diagnostic = (String) lastFailureDiagnostic.invoke(policy);
        assertFalse(diagnostic.contains("LIVE_HOOK_KEY"));
        assertTrue(diagnostic.contains("apiKey=***"));
    }

    private static Class<?> assertClassExists(String className) throws Exception {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            fail("Missing hook failure policy class: " + className);
            throw exception;
        }
    }
}
