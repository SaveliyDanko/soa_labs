package ru.itmo.soa.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.sentry.IScopes;
import io.sentry.ScopeCallback;
import io.sentry.spring.webflux.SentryWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ErrorReportingTest {
    private org.springframework.beans.factory.ObjectProvider<IScopes> provider(IScopes scopes) {
        return new org.springframework.beans.factory.support.StaticListableBeanFactory(
                java.util.Map.of("scopes", scopes)).getBeanProvider(IScopes.class);
    }

    @Test void reportsServerErrorsOnceUsingTheRequestScopeAfterAsyncWork() {
        IScopes global = mock(IScopes.class);
        IScopes request = mock(IScopes.class);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/study-groups"));
        exchange.getAttributes().put(SentryWebFilter.SENTRY_SCOPES_KEY, request);
        var reporter = new ApiErrorReporter(provider(global), new MockEnvironment().withProperty("spring.application.name", "study-groups-service"));
        var handler = new ApiExceptionHandler(new ObjectMapper().registerModule(new JavaTimeModule()), reporter);
        var failure = new IllegalStateException("database password must never reach the response");
        Mono.delay(java.time.Duration.ofMillis(1)).publishOn(Schedulers.parallel())
                .flatMap(ignored -> handler.handle(exchange, failure)).block();
        verify(request, times(1)).captureException(eq(failure), any(ScopeCallback.class));
        verifyNoInteractions(global);
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("INTERNAL_SERVER_ERROR"));
        assertFalse(body.contains("password"));
    }

    @Test void ignoresExpectedClientErrors() {
        IScopes scopes = mock(IScopes.class);
        var reporter = new ApiErrorReporter(provider(scopes), new MockEnvironment());
        for (ApiException error : new ApiException[]{new ApiException(ErrorCode.STUDY_GROUP_NOT_FOUND),
                ApiException.validation(java.util.Map.of("name", "invalid")), new ApiException(ErrorCode.MALFORMED_JSON)}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/study-groups"));
            new ApiExceptionHandler(new ObjectMapper().registerModule(new JavaTimeModule()), reporter).handle(exchange, error).block();
        }
        verifyNoInteractions(scopes);
    }

    @Test void reportsHandledUpstreamFailures() {
        IScopes scopes = mock(IScopes.class);
        var reporter = new ApiErrorReporter(provider(scopes), new MockEnvironment());
        var error = new ApiException(ErrorCode.STUDY_GROUPS_SERVICE_UNAVAILABLE);
        reporter.capture(error, error, MockServerWebExchange.from(MockServerHttpRequest.get("/isu/group/1")));
        verify(scopes).captureException(eq(error), any(ScopeCallback.class));
    }
}
