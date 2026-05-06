package dev.priceontop.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import org.junit.Test;

public final class ClockTextDecoratorTest {
    @Test
    public void appendOnce() throws Exception {
        assertEquals("12:30 · BTC $65,000", decorate("12:30", "BTC $65,000"));
    }

    @Test
    public void preventDuplicateAppend() throws Exception {
        String decorated = decorate("12:30", "BTC $65,000");

        assertEquals("12:30 · BTC $65,000", decorate(decorated, "BTC $65,000"));
    }

    @Test
    public void emptyDisplayReturnsOriginalClock() throws Exception {
        assertEquals("12:30", decorate("12:30", ""));
        assertEquals("12:30", decorate("12:30 · BTC $65,000", ""));
    }

    @Test
    public void replacesExistingSuffixAndKeepsFormatterStaleMarker() throws Exception {
        assertEquals("12:30 · ETH $3,200", decorate("12:30 · BTC $65,000", "ETH $3,200"));
        assertEquals("12:30 · BTC ~$65,000", decorate("12:30", "BTC ~$65,000"));
    }

    private static String decorate(String clockText, String displayText) throws Exception {
        Class<?> decoratorClass = assertClassExists("dev.priceontop.xposed.ClockTextDecorator");
        Object decorator = decoratorClass.getConstructor().newInstance();
        Method decorate = decoratorClass.getMethod("decorate", CharSequence.class, String.class);

        return (String) decorate.invoke(decorator, clockText, displayText);
    }

    private static Class<?> assertClassExists(String className) throws Exception {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            fail("Missing clock text decorator class: " + className);
            throw exception;
        }
    }
}
