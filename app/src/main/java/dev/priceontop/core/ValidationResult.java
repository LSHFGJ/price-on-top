package dev.priceontop.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValidationResult {
    private static final ValidationResult VALID = new ValidationResult(List.of());

    private final List<String> errors;

    private ValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public static ValidationResult valid() {
        return VALID;
    }

    public static ValidationResult fromErrors(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return valid();
        }
        return new ValidationResult(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return errors;
    }

    @Override
    public String toString() {
        return "ValidationResult{isValid=" + isValid() + ", errors=" + DiagnosticsRedactor.redact(String.valueOf(errors)) + '}';
    }
}
