package ru.itmo.soa.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.validation.ConstraintViolationException;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;
import ru.itmo.soa.model.ApiModels.ValidationErrorResponse;

/** Runs before Sentry's fallback handler (-2); reports server errors exactly once. */
@Order(-3)
public class ApiExceptionHandler implements ErrorWebExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ObjectMapper json;
    private final ApiErrorReporter reporter;

    public ApiExceptionHandler(ObjectMapper json, ApiErrorReporter reporter) {
        this.json = json;
        this.reporter = reporter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) return Mono.error(exception);
        return Mono.defer(() -> {
            ApiException error = classify(exception);
            if (error.getStatus() >= 500) LOG.error("Ошибка API {}", error.getCode(), exception);
            reporter.capture(exception, error, exchange);
            HttpHeaders headers = exception instanceof ResponseStatusException
                    ? ((ResponseStatusException) exception).getResponseHeaders() : new HttpHeaders();
            ResponseEntity<Object> response = response(error, exchange.getRequest().getPath().value(), headers);
            exchange.getResponse().setStatusCode(response.getStatusCode());
            exchange.getResponse().getHeaders().putAll(response.getHeaders());
            try {
                byte[] body = json.writeValueAsBytes(response.getBody());
                return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                return Mono.error(failure);
            }
        });
    }

    private ApiException classify(Throwable exception) {
        if (exception instanceof ApiException) return (ApiException) exception;
        if (exception instanceof WebExchangeBindException) {
            Map<String, String> violations = new LinkedHashMap<>();
            ((WebExchangeBindException) exception).getBindingResult().getFieldErrors().stream()
                    .sorted(Comparator.comparing(FieldError::getField).thenComparing(FieldError::getDefaultMessage))
                    .forEach(error -> violations.putIfAbsent(error.getField(), error.getDefaultMessage()));
            return ApiException.validation(violations);
        }
        if (exception instanceof ConstraintViolationException) {
            return ApiException.validation(RequestValidation.violations(
                    ((ConstraintViolationException) exception).getConstraintViolations()));
        }
        if (exception instanceof ServerWebInputException) {
            return new ApiException(ErrorCode.MALFORMED_JSON, jsonErrorDetails(exception), exception);
        }
        if (exception instanceof ResponseStatusException) {
            return ApiException.http(((ResponseStatusException) exception).getRawStatusCode());
        }
        return new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, Map.of(), exception);
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
