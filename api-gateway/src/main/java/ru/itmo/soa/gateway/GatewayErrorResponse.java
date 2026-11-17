package ru.itmo.soa.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.itmo.soa.error.ApiErrorReporter;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;

@Component
public class GatewayErrorResponse {
    private final ObjectMapper json;
    private final ApiErrorReporter reporter;

    public GatewayErrorResponse(ObjectMapper json, ApiErrorReporter reporter) {
        this.json = json;
        this.reporter = reporter;
    }

    public Mono<Void> write(ServerWebExchange exchange, ServerHttpResponse response) {
        return Mono.defer(() -> {
            ApiException error = new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE);
            reporter.capture(error, error, exchange);
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            HttpHeaders headers = response.getHeaders();
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.remove(HttpHeaders.CONTENT_ENCODING);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.CONTENT_LANGUAGE, "ru");
            try {
                byte[] body = json.writeValueAsBytes(new ApiErrorResponse(error.getStatus(), error.getCode(),
                        error.getMessage(), exchange.getRequest().getPath().value(), Map.of()));
                return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
            } catch (JsonProcessingException failure) {
                return Mono.error(failure);
            }
        });
    }
}
