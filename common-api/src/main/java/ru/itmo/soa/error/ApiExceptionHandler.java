package ru.itmo.soa.error;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;
import ru.itmo.soa.model.ApiModels.ValidationErrorResponse;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = Logger.getLogger(ApiExceptionHandler.class.getName());

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> api(ApiException exception, HttpServletRequest request) {
        return response(exception, request.getRequestURI(), new HttpHeaders());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> validation(ConstraintViolationException exception, HttpServletRequest request) {
        return api(ApiException.validation(RequestValidation.violations(exception.getConstraintViolations())), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception, HttpServletRequest request) {
        LOG.log(Level.SEVERE, "Необработанная ошибка API", exception);
        return api(new ApiException(ErrorCode.INTERNAL_SERVER_ERROR), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField).thenComparing(FieldError::getDefaultMessage))
                .forEach(error -> violations.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(ApiException.validation(violations), path(request), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        ApiException error = new ApiException(ErrorCode.MALFORMED_JSON, jsonErrorDetails(exception), exception);
        return response(error, path(request), headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        return response(ApiException.http(status.value()), path(request), headers);
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private static Map<String, String> jsonErrorDetails(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnrecognizedPropertyException) {
                UnrecognizedPropertyException unknown = (UnrecognizedPropertyException) cause;
                String field = unknown.getPath().stream()
                        .map(ref -> ref.getFieldName() == null ? Integer.toString(ref.getIndex()) : ref.getFieldName())
                        .collect(Collectors.joining("."));
                return Map.of("field", field);
            }
        }
        return Map.of();
    }

    static ResponseEntity<Object> response(ApiException exception, String path, HttpHeaders headers) {
        ApiErrorResponse body = exception.getViolations().isEmpty()
                ? new ApiErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(), path, exception.getDetails())
                : new ValidationErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(), path, exception.getViolations());
        HttpHeaders result = new HttpHeaders();
        result.putAll(headers);
        result.remove(HttpHeaders.CONTENT_LENGTH);
        result.remove(HttpHeaders.CONTENT_ENCODING);
        result.set(HttpHeaders.CONTENT_LANGUAGE, "ru");
        result.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(body, result, HttpStatus.valueOf(exception.getStatus()));
    }
}
