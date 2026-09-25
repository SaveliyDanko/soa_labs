package ru.itmo.soa.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class RequestValidation {
    private RequestValidation() {}

    public static int positiveId(String value, String name) {
        return integer(value, name, null, 1, Integer.MAX_VALUE);
    }

    public static int integer(String value, String name, Integer defaultValue, int min, int max) {
        if (value == null) {
            if (defaultValue != null) return defaultValue;
            throw new ApiException(ErrorCode.MISSING_PARAMETER, Map.of("parameter", name));
        }
        final int result;
        try {
            result = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER_TYPE,
                    Map.of("parameter", name, "value", value, "expectedType", "int"), exception);
        }
        if (result < min || result > max) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER_VALUE,
                    Map.of("parameter", name, "value", value,
                            "min", Integer.toString(min), "max", Integer.toString(max)));
        }
        return result;
    }

    public static String requiredText(String value, String name) {
        if (value == null) {
            throw new ApiException(ErrorCode.MISSING_PARAMETER, Map.of("parameter", name));
        }
        if (value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER_VALUE, Map.of("parameter", name, "value", value));
        }
        return value;
    }

    public static <T> void validate(T request, Validator validator) {
        if (request == null) {
            throw ApiException.validation(Map.of("request", ValidationMessages.REQUIRED));
        }
        Map<String, String> violations = violations(validator.validate(request));
        if (!violations.isEmpty()) throw ApiException.validation(violations);
    }

    public static Map<String, String> violations(Set<? extends ConstraintViolation<?>> violations) {
        Map<String, String> fields = new LinkedHashMap<>();
        violations.stream()
                .sorted(Comparator.comparing((ConstraintViolation<?> violation) -> violation.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .forEach(violation -> fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return fields;
    }
}
