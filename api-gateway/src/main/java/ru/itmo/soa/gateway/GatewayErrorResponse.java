package ru.itmo.soa.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.zuul.context.RequestContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;

@Component
public class GatewayErrorResponse {
    private final ObjectMapper json;

    public GatewayErrorResponse(ObjectMapper json) {
        this.json = json;
    }

    public byte[] body(String path) {
        ErrorCode code = ErrorCode.UPSTREAM_UNAVAILABLE;
        try {
            return json.writeValueAsBytes(new ApiErrorResponse(code.status(), code.name(), code.message(), path, Map.of()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сформировать ответ шлюза", exception);
        }
    }

    public void apply(RequestContext context) {
        if (context.getResponseDataStream() != null) {
            try { context.getResponseDataStream().close(); }
            catch (IOException ignored) { /* Соединение с недоступным upstream уже закрыто. */ }
        }
        context.setResponseDataStream(null);
        context.setResponseGZipped(false);
        context.setResponseStatusCode(503);
        context.setResponseBody(new String(body(context.getRequest().getRequestURI()), StandardCharsets.UTF_8));
        Set<String> replaced = Set.of("content-type", "content-length", "content-encoding", "content-language");
        context.getZuulResponseHeaders().removeIf(header -> replaced.contains(header.first().toLowerCase(java.util.Locale.ROOT)));
        context.addZuulResponseHeader("Content-Type", "application/json;charset=UTF-8");
        context.addZuulResponseHeader("Content-Language", "ru");
    }
}
