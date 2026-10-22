package ru.itmo.soa.gateway;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.stereotype.Component;

@Component
public class GatewayHeadersFilter extends ZuulFilter {
    private final GatewayErrorResponse errors;

    public GatewayHeadersFilter(GatewayErrorResponse errors) {
        this.errors = errors;
    }

    @Override
    public String filterType() {
        return "post";
    }
    @Override
    public int filterOrder() {
        return 900;
    }
    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        RequestContext context = RequestContext.getCurrentContext();
        // HAProxy при отсутствии здоровых реплик возвращает HTML: заменяем общим JSON-контрактом.
        boolean json = context.getZuulResponseHeaders().stream().anyMatch(header ->
                "Content-Type".equalsIgnoreCase(header.first()) && header.second().contains("application/json"));
        if (context.getResponseStatusCode() >= 500 && !json) errors.apply(context);
        context.addZuulResponseHeader("X-Gateway", "zuul");
        return null;
    }
}
