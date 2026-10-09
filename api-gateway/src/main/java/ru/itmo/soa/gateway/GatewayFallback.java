package ru.itmo.soa.gateway;

import org.springframework.cloud.netflix.zuul.filters.route.FallbackProvider;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import com.netflix.zuul.context.RequestContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Component
public class GatewayFallback implements FallbackProvider {
    private final GatewayErrorResponse errors;

    public GatewayFallback(GatewayErrorResponse errors) { this.errors = errors; }

    @Override public String getRoute() { return "*"; }

    @Override
    public ClientHttpResponse fallbackResponse(String route, Throwable cause) {
        byte[] body = errors.body(RequestContext.getCurrentContext().getRequest().getRequestURI());
        return new ClientHttpResponse() {
            @Override public HttpStatus getStatusCode() { return HttpStatus.SERVICE_UNAVAILABLE; }
            @Override public int getRawStatusCode() { return 503; }
            @Override public String getStatusText() { return "Сервис временно недоступен"; }
            @Override public void close() {}
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setContentLanguage(java.util.Locale.forLanguageTag("ru"));
                return headers;
            }
        };
    }
}
