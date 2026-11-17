package ru.itmo.soa.gateway;

import java.util.Map;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;

@Component
public class GatewayErrorFilter implements GlobalFilter, Ordered {
    @Override public int getOrder() { return -3; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).onErrorMap(failure -> !(failure instanceof ApiException)
                        && (!(failure instanceof ResponseStatusException)
                            || ((ResponseStatusException) failure).getRawStatusCode() >= 500),
                failure -> new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, Map.of(), failure));
    }
}
