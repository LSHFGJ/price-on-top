package dev.priceontop.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import org.junit.Test;

public final class RomFamilyTest {
    @Test
    public void detectsAospPixelAndLineageInputs() throws Exception {
        assertDetected("AOSP_PIXEL_LINEAGE", "google/panther/panther:14/AP1A", "Google", "Pixel");
        assertDetected("AOSP_PIXEL_LINEAGE", "lineage/redfin/redfin:13/TQ3A", "LineageOS", "lineage");
        assertDetected("AOSP_PIXEL_LINEAGE", "aosp_cf_x86_64_phone-userdebug", "AOSP", "generic");
    }

    @Test
    public void detectsMiuiAndHyperOsInputs() throws Exception {
        assertDetected("MIUI_HYPEROS", "xiaomi/mondrian_global/mondrian:14/UKQ1 hyperos", "Xiaomi", "Redmi");
        assertDetected("MIUI_HYPEROS", "poco/fuxi/fuxi:13/TKQ1 miui", "POCO", "poco");
    }

    @Test
    public void returnsUnknownForUnrecognizedOrBlankInputs() throws Exception {
        assertDetected("UNKNOWN", "samsung/dm1q/dm1q:14/UP1A", "Samsung", "Galaxy");
        assertDetected("UNKNOWN", null, null, null);
        assertDetected("UNKNOWN", " ", " ", " ");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertDetected(
        String expectedName,
        String fingerprint,
        String manufacturer,
        String brand
    ) throws Exception {
        Class<?> romFamilyClass = assertClassExists("dev.priceontop.xposed.RomFamily");
        Method detect = romFamilyClass.getMethod("detect", String.class, String.class, String.class);
        Object expected = Enum.valueOf((Class<Enum>) romFamilyClass.asSubclass(Enum.class), expectedName);

        assertEquals(expected, detect.invoke(null, fingerprint, manufacturer, brand));
    }

    private static Class<?> assertClassExists(String className) throws Exception {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            fail("Missing ROM family class: " + className);
            throw exception;
        }
    }
}
