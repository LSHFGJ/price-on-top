package dev.priceontop.xposed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import org.junit.Test;

public final class XposedScopeGuardTest {
    @Test
    public void acceptsOnlySystemUiPackageAndProcess() throws Exception {
        Class<?> guardClass = assertClassExists("dev.priceontop.xposed.XposedScopeGuard");
        Method shouldRegister = guardClass.getMethod("shouldRegister", String.class, String.class);

        assertTrue((Boolean) shouldRegister.invoke(null, "com.android.systemui", "com.android.systemui"));

        String[] ignoredNames = {"android", "com.miui.home", "dev.priceontop"};
        for (String ignoredName : ignoredNames) {
            assertFalse(ignoredName + " package must be ignored",
                (Boolean) shouldRegister.invoke(null, ignoredName, "com.android.systemui"));
            assertFalse(ignoredName + " process must be ignored",
                (Boolean) shouldRegister.invoke(null, "com.android.systemui", ignoredName));
            assertFalse(ignoredName + " package/process pair must be ignored",
                (Boolean) shouldRegister.invoke(null, ignoredName, ignoredName));
        }

        assertFalse((Boolean) shouldRegister.invoke(null, null, "com.android.systemui"));
        assertFalse((Boolean) shouldRegister.invoke(null, "com.android.systemui", null));
    }

    private static Class<?> assertClassExists(String className) throws Exception {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            fail("Missing xposed guard class: " + className);
            throw exception;
        }
    }
}
