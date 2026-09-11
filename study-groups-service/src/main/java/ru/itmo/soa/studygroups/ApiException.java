package ru.itmo.soa.studygroups;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, String> details;
    private final Map<String, String> violations;

    public ApiException(int status, String code, String message) {
        this(status, code, message, Map.of(), Map.of(), null);
    }

    public ApiException(int status, String code, String message, Map<String, String> details) {
        this(status, code, message, details, Map.of(), null);
    }

    public ApiException(int status, String code, String message, Map<String, String> details,
                        Map<String, String> violations, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.details = new LinkedHashMap<>(details);
        this.violations = new LinkedHashMap<>(violations);
    }

    public static ApiException validation(Map<String, String> violations) {
        return new ApiException(422, "VALIDATION_FAILED",
                "JSON корректен синтаксически, но поля нарушают ограничения модели",
                Map.of(), violations, null);
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getDetails() { return details; }
    public Map<String, String> getViolations() { return violations; }
}
