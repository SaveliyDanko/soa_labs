package ru.itmo.soa.error;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;
import ru.itmo.soa.model.ApiModels.ValidationErrorResponse;

import java.util.Set;

final class ErrorResponses {
    private static final Set<String> ENTITY_HEADERS = Set.of(HttpHeaders.CONTENT_LENGTH,
            HttpHeaders.CONTENT_ENCODING, HttpHeaders.ETAG, HttpHeaders.LAST_MODIFIED,
            HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_LANGUAGE);

    private ErrorResponses() {}

    static ApiErrorResponse body(ApiException exception, String path) {
        return exception.getViolations().isEmpty()
                ? new ApiErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(),
                        path, exception.getDetails())
                : new ValidationErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(),
                        path, exception.getViolations());
    }

    static void prepareHeaders(MultivaluedMap<String, Object> headers) {
        headers.keySet().removeIf(name -> ENTITY_HEADERS.stream().anyMatch(name::equalsIgnoreCase));
        headers.putSingle(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_TYPE);
        headers.putSingle(HttpHeaders.CONTENT_LANGUAGE, "ru");
    }
}
