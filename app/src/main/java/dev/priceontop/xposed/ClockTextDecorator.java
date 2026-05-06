package dev.priceontop.xposed;

public final class ClockTextDecorator {
    public static final String SEPARATOR = " · ";

    public String decorate(CharSequence currentClockText, String displayText) {
        String baseClockText = stripExistingSuffix(currentClockText);
        String display = displayText == null ? "" : displayText.trim();
        if (display.isEmpty()) {
            return baseClockText;
        }
        if (baseClockText.isEmpty()) {
            return display;
        }
        return baseClockText + SEPARATOR + display;
    }

    public String stripExistingSuffix(CharSequence currentClockText) {
        String clockText = currentClockText == null ? "" : currentClockText.toString();
        int separatorIndex = clockText.indexOf(SEPARATOR);
        if (separatorIndex < 0) {
            return clockText;
        }
        return clockText.substring(0, separatorIndex);
    }
}
