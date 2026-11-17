package ru.itmo.soa.error;

import io.sentry.IScopes;
import io.sentry.NoOpScopes;
import org.springframework.beans.factory.ObjectProvider;
import io.sentry.spring.webflux.SentryWebFilter;
import org.springframework.core.env.Environment;
import org.springframework.web.server.ServerWebExchange;

/** Uses the request scope stored by Sentry's WebFlux filter, including after async I/O. */
public class ApiErrorReporter {
    private final ObjectProvider<IScopes> scopes;
    private final String service;
    private final String instance;

    public ApiErrorReporter(ObjectProvider<IScopes> scopes, Environment environment) {
        this.scopes = scopes;
        this.service = environment.getProperty("spring.application.name", "service");
        this.instance = environment.getProperty("lab.instance-id", service);
    }

    public void capture(Throwable exception, ApiException error, ServerWebExchange exchange) {
        if (error.getStatus() < 500) return;
        IScopes requestScopes = exchange.getAttributeOrDefault(SentryWebFilter.SENTRY_SCOPES_KEY,
                scopes.getIfAvailable(NoOpScopes::getInstance));
        requestScopes.captureException(exception, scope -> {
            scope.setTag("service", service);
            scope.setTag("instance", instance);
            scope.setTag("error.code", error.getCode());
            scope.setTag("http.status_code", Integer.toString(error.getStatus()));
            scope.setTag("http.method", exchange.getRequest().getMethodValue());
        });
    }
}
