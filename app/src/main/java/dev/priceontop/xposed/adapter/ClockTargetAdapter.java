package dev.priceontop.xposed.adapter;

import dev.priceontop.xposed.ClockTextDecorator;
import dev.priceontop.xposed.RomFamily;
import java.util.List;

public interface ClockTargetAdapter {
    String name();

    boolean supportsRom(RomFamily romFamily);

    boolean supportsTarget(TargetKind targetKind);

    Result decorate(Object target, String displayText, ClockTextDecorator decorator);

    static List<ClockTargetAdapter> orderedFor(RomFamily romFamily) {
        RomFamily family = romFamily == null ? RomFamily.UNKNOWN : romFamily;
        if (family == RomFamily.MIUI_HYPEROS) {
            return List.of(new MiuiHyperOsClockAdapter(), new AospClockAdapter());
        }
        return List.of(new AospClockAdapter());
    }

    enum TargetKind {
        COLLAPSED_STATUS_BAR,
        LOCKSCREEN,
        AOD,
        EXPANDED_QS,
        UNKNOWN
    }

    enum Status {
        DECORATED,
        UNSUPPORTED,
        NO_OP
    }

    final class Result {
        private final Status status;
        private final String adapterName;
        private final String diagnostic;

        private Result(Status status, String adapterName, String diagnostic) {
            this.status = status == null ? Status.UNSUPPORTED : status;
            this.adapterName = adapterName == null ? "unknown" : adapterName;
            this.diagnostic = diagnostic == null ? "" : diagnostic;
        }

        public static Result decorated(String adapterName) {
            return new Result(Status.DECORATED, adapterName, "decorated collapsed status-bar clock");
        }

        public static Result unsupported(String adapterName, String diagnostic) {
            return new Result(Status.UNSUPPORTED, adapterName, diagnostic);
        }

        public static Result noOp(String adapterName, String diagnostic) {
            return new Result(Status.NO_OP, adapterName, diagnostic);
        }

        public Status status() {
            return status;
        }

        public String adapterName() {
            return adapterName;
        }

        public String diagnostic() {
            return diagnostic;
        }

        public boolean isSupported() {
            return status != Status.UNSUPPORTED;
        }

        public boolean isMutated() {
            return status == Status.DECORATED;
        }
    }

    interface MutableClockTarget {
        String targetClassName();

        TargetKind targetKind();

        CharSequence getText();

        void setText(CharSequence text);
    }
}
