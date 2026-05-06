package dev.priceontop.core;

public final class DisplayFormat {
    public static final String DEFAULT_SEPARATOR = " · ";
    public static final String DEFAULT_TEMPLATE = "{SYMBOL} {PRICE}";

    private final String separator;
    private final String template;

    private DisplayFormat(String separator, String template) {
        this.separator = separator;
        this.template = template;
    }

    public static DisplayFormat statusBarDefault() {
        return new DisplayFormat(DEFAULT_SEPARATOR, DEFAULT_TEMPLATE);
    }

    public String separator() {
        return separator;
    }

    public String template() {
        return template;
    }

    @Override
    public String toString() {
        return "DisplayFormat{separator='" + separator + '\'' + ", template='" + template + '\'' + '}';
    }
}
