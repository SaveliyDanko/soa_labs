package ru.itmo.soa.gateway;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.stereotype.Component;

@Component
public class GatewayErrorFilter extends ZuulFilter {
    private final GatewayErrorResponse errors;

    public GatewayErrorFilter(GatewayErrorResponse errors) { this.errors = errors; }

    @Override public String filterType() { return "error"; }
    @Override public int filterOrder() { return -1; }
    @Override public boolean shouldFilter() { return RequestContext.getCurrentContext().getThrowable() != null; }

    @Override
    public Object run() {
        RequestContext context = RequestContext.getCurrentContext();
        errors.apply(context);
        context.remove("throwable");
        context.setSendZuulResponse(false);
        return null;
    }
}
