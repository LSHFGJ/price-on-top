package dev.priceontop.storage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import org.junit.Test;

public final class ProviderAccessTest {
    private static final String DUMMY_RAW_SECRET = "DUMMY_RAW_SECRET_VALUE";

    @Test
    public void allowsOwnUid() throws Exception {
        assertTrue(isAllowed(10_001, 10_001, new String[] {"dev.priceontop"}));
    }

    @Test
    public void allowsSystemUiPackageForCrossUidReads() throws Exception {
        assertTrue(isAllowed(10_200, 10_001, new String[] {"com.android.systemui"}));
    }

    @Test
    public void rejectsUntrustedCaller() throws Exception {
        boolean allowed = isAllowed(10_300, 10_001, new String[] {"com.example.untrusted"});
        String diagnostic = deniedDiagnostic(
            10_300,
            new String[] {"com.example.untrusted"},
            "token=" + DUMMY_RAW_SECRET
        );

        assertFalse(allowed);
        assertFalse(diagnostic.contains(DUMMY_RAW_SECRET));
        assertTrue(diagnostic.contains("denied"));
        assertTrue(diagnostic.contains("***"));
    }

    @Test
    public void rejectsUidWithoutPackageNames() throws Exception {
        assertFalse(isAllowed(10_300, 10_001, null));
    }

    private static boolean isAllowed(int callerUid, int ownUid, String[] packageNames) throws Exception {
        Class<?> access = providerAccessClass();
        Method method = access.getMethod("isAllowed", int.class, int.class, String[].class);
        return (Boolean) method.invoke(null, callerUid, ownUid, packageNames);
    }

    private static String deniedDiagnostic(int callerUid, String[] packageNames, String detail) throws Exception {
        Class<?> access = providerAccessClass();
        Method method = access.getMethod("deniedDiagnostic", int.class, String[].class, String.class);
        return (String) method.invoke(null, callerUid, packageNames, detail);
    }

    private static Class<?> providerAccessClass() throws Exception {
        try {
            return Class.forName("dev.priceontop.storage.ProviderAccess");
        } catch (ClassNotFoundException exception) {
            fail("Missing provider access helper: dev.priceontop.storage.ProviderAccess");
            throw exception;
        }
    }
}
