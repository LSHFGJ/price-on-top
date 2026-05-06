package dev.priceontop.xposed.adapter;

import dev.priceontop.xposed.ClockTextDecorator;
import dev.priceontop.xposed.RomFamily;
import dev.priceontop.xposed.XposedHookDiagnostics;
import java.lang.reflect.Method;
import java.util.Locale;

public final class MiuiHyperOsClockAdapter implements ClockTargetAdapter {
    private static final String ADAPTER_NAME = "miui-hyperos-clock";

    @Override
    public String name() {
        return ADAPTER_NAME;
    }

    @Override
    public boolean supportsRom(RomFamily romFamily) {
        return romFamily == RomFamily.MIUI_HYPEROS;
    }

    @Override
    public boolean supportsTarget(TargetKind targetKind) {
        return targetKind == TargetKind.COLLAPSED_STATUS_BAR;
    }

    @Override
    public Result decorate(Object target, String displayText, ClockTextDecorator decorator) {
        try {
            if (target == null || decorator == null) {
                return Result.unsupported(name(), "missing clock target or decorator");
            }
            ReflectiveTextTarget textTarget = ReflectiveTextTarget.from(target);
            if (textTarget == null) {
                return Result.unsupported(name(), "missing text accessors");
            }
            if (!isCollapsedStatusBarClock(textTarget)) {
                return Result.unsupported(name(), "unsupported clock target");
            }

            String currentText = textTarget.getText();
            if (currentText == null) {
                return Result.unsupported(name(), "unreadable clock text");
            }
            String decoratedText = decorator.decorate(currentText, displayText);
            if (currentText.equals(decoratedText)) {
                return Result.noOp(name(), "clock text already current");
            }
            if (!textTarget.setText(decoratedText)) {
                return Result.unsupported(name(), "unwritable clock text");
            }
            return Result.decorated(name());
        } catch (Throwable failure) {
            return Result.unsupported(name(), XposedHookDiagnostics.failure(failure));
        }
    }

    private boolean isCollapsedStatusBarClock(ReflectiveTextTarget target) {
        if (target.targetKind() != TargetKind.UNKNOWN && !supportsTarget(target.targetKind())) {
            return false;
        }
        return isSupportedClassName(target.className());
    }

    private boolean isSupportedClassName(String className) {
        String normalized = normalize(className);
        if (isNonCollapsedClockName(normalized)) {
            return false;
        }
        return normalized.equals("com.android.systemui.statusbar.views.miuiclock")
            || normalized.equals("com.android.systemui.statusbar.phone.miuiphonestatusbarclock")
            || normalized.equals("com.miui.systemui.statusbar.miuiclock")
            || (normalized.contains("miui") && normalized.contains("statusbar") && normalized.endsWith("clock"));
    }

    private static boolean isNonCollapsedClockName(String normalizedClassName) {
        return normalizedClassName.contains("keyguard")
            || normalizedClassName.contains("lockscreen")
            || normalizedClassName.contains("aod")
            || normalizedClassName.contains("ambient")
            || normalizedClassName.contains("quicksettings")
            || normalizedClassName.contains("quick_settings")
            || normalizedClassName.contains("expandedqs");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static final class ReflectiveTextTarget {
        private final Object target;
        private final String className;
        private final TargetKind targetKind;
        private final Method getText;
        private final Method setText;
        private final MutableClockTarget mutableTarget;

        private ReflectiveTextTarget(Object target, String className, TargetKind targetKind, Method getText, Method setText) {
            this.target = target;
            this.className = className;
            this.targetKind = targetKind == null ? TargetKind.UNKNOWN : targetKind;
            this.getText = getText;
            this.setText = setText;
            this.mutableTarget = null;
        }

        private ReflectiveTextTarget(MutableClockTarget mutableTarget) {
            this.target = mutableTarget;
            this.className = mutableTarget.targetClassName();
            this.targetKind = mutableTarget.targetKind() == null ? TargetKind.UNKNOWN : mutableTarget.targetKind();
            this.getText = null;
            this.setText = null;
            this.mutableTarget = mutableTarget;
        }

        static ReflectiveTextTarget from(Object target) {
            if (target instanceof MutableClockTarget mutableTarget) {
                return new ReflectiveTextTarget(mutableTarget);
            }
            Class<?> targetClass = target.getClass();
            Method getText = findNoArgMethod(targetClass, "getText");
            Method setText = findSetTextMethod(targetClass);
            if (getText == null || setText == null) {
                return null;
            }
            return new ReflectiveTextTarget(
                target,
                targetClass.getName(),
                TargetKind.UNKNOWN,
                getText,
                setText
            );
        }

        String className() {
            return className;
        }

        TargetKind targetKind() {
            return targetKind;
        }

        String getText() {
            try {
                Object value = mutableTarget == null ? getText.invoke(target) : mutableTarget.getText();
                return value == null ? "" : value.toString();
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean setText(String text) {
            try {
                if (mutableTarget != null) {
                    mutableTarget.setText(text);
                    return true;
                }
                setText.invoke(target, text);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static Method findNoArgMethod(Class<?> targetClass, String methodName) {
            for (Method method : targetClass.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return method;
                }
            }
            for (Method method : targetClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return method;
                }
            }
            return null;
        }

        private static Method findSetTextMethod(Class<?> targetClass) {
            Method method = findSetTextMethod(targetClass.getMethods());
            if (method != null) {
                return method;
            }
            method = findSetTextMethod(targetClass.getDeclaredMethods());
            if (method != null) {
                return method;
            }
            return null;
        }

        private static Method findSetTextMethod(Method[] methods) {
            for (Method method : methods) {
                if (!method.getName().equals("setText") || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (parameterType.isAssignableFrom(String.class) || parameterType.isAssignableFrom(CharSequence.class)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            return null;
        }
    }
}
