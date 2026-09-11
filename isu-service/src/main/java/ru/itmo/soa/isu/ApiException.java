package ru.itmo.soa.isu;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, String> details;

    public ApiException(int status, String code, String message) {
        this(status, code, message, Map.of(), null);
    }

    public ApiException(int status, String code, String message, Map<String, String> details) {
        this(status, code, message, details, null);
    }

    public ApiException(int status, String code, String message, Map<String, String> details, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.details = new LinkedHashMap<>(details);
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getDetails() { return details; }
}
