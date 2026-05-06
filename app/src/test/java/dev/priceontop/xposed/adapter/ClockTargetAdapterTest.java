package dev.priceontop.xposed.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.systemui.statusbar.policy.Clock;
import dev.priceontop.xposed.RomFamily;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

public final class ClockTargetAdapterTest {
    @Test
    public void adapterOrderingUsesRomSpecificFirstForMiuiAndAospFallback() throws Exception {
        List<?> miuiAdapters = orderedAdapters(RomFamily.MIUI_HYPEROS);
        assertEquals("MiuiHyperOsClockAdapter", miuiAdapters.get(0).getClass().getSimpleName());
        assertEquals("AospClockAdapter", miuiAdapters.get(1).getClass().getSimpleName());

        List<?> aospAdapters = orderedAdapters(RomFamily.AOSP_PIXEL_LINEAGE);
        assertEquals("AospClockAdapter", aospAdapters.get(0).getClass().getSimpleName());

        List<?> unknownAdapters = orderedAdapters(RomFamily.UNKNOWN);
        assertEquals("AospClockAdapter", unknownAdapters.get(0).getClass().getSimpleName());
    }

    @Test
    public void neverClaimsNonCollapsedTargets() throws Exception {
        Object adapter = newAdapter("dev.priceontop.xposed.adapter.AospClockAdapter");
        Class<?> adapterClass = assertClassExists("dev.priceontop.xposed.adapter.ClockTargetAdapter");
        Class<?> targetKindClass = assertClassExists("dev.priceontop.xposed.adapter.ClockTargetAdapter$TargetKind");
        Method supportsTarget = adapterClass.getMethod("supportsTarget", targetKindClass);

        assertTrue((Boolean) supportsTarget.invoke(adapter, enumValue(targetKindClass, "COLLAPSED_STATUS_BAR")));
        assertFalse((Boolean) supportsTarget.invoke(adapter, enumValue(targetKindClass, "LOCKSCREEN")));
        assertFalse((Boolean) supportsTarget.invoke(adapter, enumValue(targetKindClass, "AOD")));
        assertFalse((Boolean) supportsTarget.invoke(adapter, enumValue(targetKindClass, "EXPANDED_QS")));
    }

    @Test
    public void unsupportedTargetNoOps() throws Exception {
        Object adapter = newAdapter("dev.priceontop.xposed.adapter.AospClockAdapter");
        Object result = decorate(adapter, new UnsupportedClockTarget(), "BTC $65,000");

        assertEquals("UNSUPPORTED", resultStatus(result));
        assertFalse(resultSupported(result));
        assertFalse(resultMutated(result));
    }

    @Test
    public void aospAdapterDecoratesCollapsedClockTarget() throws Exception {
        Object adapter = newAdapter("dev.priceontop.xposed.adapter.AospClockAdapter");
        Clock clock = new Clock("12:30");
        Object result = decorate(adapter, clock, "BTC $65,000");

        assertEquals("DECORATED", resultStatus(result));
        assertTrue(resultSupported(result));
        assertTrue(resultMutated(result));
        assertEquals("12:30 · BTC $65,000", clock.getText().toString());
    }

    @Test
    public void miuiAdapterDecoratesCollapsedClockTarget() throws Exception {
        Object adapter = newAdapter("dev.priceontop.xposed.adapter.MiuiHyperOsClockAdapter");
        com.android.systemui.statusbar.views.MiuiClock clock =
            new com.android.systemui.statusbar.views.MiuiClock("12:30");
        Object result = decorate(adapter, clock, "BTC $65,000");

        assertEquals("DECORATED", resultStatus(result));
        assertTrue(resultSupported(result));
        assertTrue(resultMutated(result));
        assertEquals("12:30 · BTC $65,000", clock.getText().toString());
    }

    @SuppressWarnings("unchecked")
    private static List<?> orderedAdapters(RomFamily romFamily) throws Exception {
        Class<?> adapterClass = assertClassExists("dev.priceontop.xposed.adapter.ClockTargetAdapter");
        Method orderedFor = adapterClass.getMethod("orderedFor", RomFamily.class);

        return (List<?>) orderedFor.invoke(null, romFamily);
    }

    private static Object newAdapter(String className) throws Exception {
        return assertClassExists(className).getConstructor().newInstance();
    }

    private static Object decorate(Object adapter, Object target, String displayText) throws Exception {
        Class<?> adapterClass = assertClassExists("dev.priceontop.xposed.adapter.ClockTargetAdapter");
        Class<?> decoratorClass = assertClassExists("dev.priceontop.xposed.ClockTextDecorator");
        Object decorator = decoratorClass.getConstructor().newInstance();
        Method decorate = adapterClass.getMethod("decorate", Object.class, String.class, decoratorClass);

        return decorate.invoke(adapter, target, displayText, decorator);
    }

    private static String resultStatus(Object result) throws Exception {
        return result.getClass().getMethod("status").invoke(result).toString();
    }

    private static boolean resultSupported(Object result) throws Exception {
        return (Boolean) result.getClass().getMethod("isSupported").invoke(result);
    }

    private static boolean resultMutated(Object result) throws Exception {
        return (Boolean) result.getClass().getMethod("isMutated").invoke(result);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static Class<?> assertClassExists(String className) throws Exception {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            fail("Missing clock target adapter class: " + className);
            throw exception;
        }
    }

    public static final class UnsupportedClockTarget {
        public CharSequence getText() {
            return "12:30";
        }

        public void setText(CharSequence text) {
            throw new AssertionError("Unsupported targets must not be mutated");
        }
    }
}
