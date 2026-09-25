package ru.itmo.soa.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiException extends RuntimeException {
    private final ErrorCode error;
    private final int status;
    private final Map<String, String> details;
    private final Map<String, String> violations;

    public ApiException(ErrorCode error) {
        this(error, Map.of());
    }

    public ApiException(ErrorCode error, Map<String, String> details) {
        this(error, details, null);
    }

    public ApiException(ErrorCode error, Map<String, String> details, Throwable cause) {
        this(error, error.status(), details, Map.of(), cause);
    }

    private ApiException(ErrorCode error, int status, Map<String, String> details,
                         Map<String, String> violations, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
        this.status = status;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.violations = Collections.unmodifiableMap(new LinkedHashMap<>(violations));
    }

    public static ApiException validation(Map<String, String> violations) {
        ErrorCode error = ErrorCode.VALIDATION_FAILED;
        return new ApiException(error, error.status(), Map.of(), violations, null);
    }

    public static ApiException http(int status) {
        ErrorCode error = ErrorCode.forHttpStatus(status);
        Map<String, String> details = error == ErrorCode.HTTP_ERROR
                ? Map.of("status", Integer.toString(status)) : Map.of();
        return new ApiException(error, status, details, Map.of(), null);
    }

    public int getStatus() { return status; }
    public String getCode() { return error.name(); }
    public Map<String, String> getDetails() { return details; }
    public Map<String, String> getViolations() { return violations; }
}
