package ru.itmo.soa.gateway;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class GatewayHeadersFilter implements GlobalFilter, Ordered {
    private final GatewayErrorResponse errors;

    public GatewayHeadersFilter(GatewayErrorResponse errors) { this.errors = errors; }
    @Override public int getOrder() { return -2; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponseDecorator response = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                MediaType type = getHeaders().getContentType();
                boolean json = type != null && (MediaType.APPLICATION_JSON.isCompatibleWith(type)
                        || type.getSubtype().endsWith("+json"));
                // HAProxy can return HTML when every ISU replica is unavailable.
                if (getRawStatusCode() != null && getRawStatusCode() >= 500 && !json) {
                    return Flux.from(body).doOnNext(DataBufferUtils::release).then(errors.write(exchange, getDelegate()));
                }
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).concatMap(publisher -> publisher));
            }
        };
        return chain.filter(exchange.mutate().response(response).build());
    }
}
