package ru.itmo.soa.isu;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

@Component
public class GatewayStudyGroupsClient implements StudyGroupsClient {
    private final WebClient client;

    public GatewayStudyGroupsClient(WebClient.Builder builder,
                                    @Value("${study-groups.gateway-url}") String baseUrl) {
        HttpClient http = HttpClient.create().disableRetry(true).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000)
                .responseTimeout(Duration.ofSeconds(12));
        this.client = builder.clientConnector(new ReactorClientHttpConnector(http))
                .baseUrl(baseUrl.replaceAll("/+$", "") + "/api/study-groups")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE).build();
    }

    @Override
    public Mono<StudyGroup> get(int id) { return execute(id, HttpMethod.GET, null, StudyGroup.class); }

    @Override
    public Mono<StudyGroup> update(int id, StudyGroupRequest request) {
        return execute(id, HttpMethod.PUT, request, StudyGroup.class);
    }

    @Override
    public Mono<Void> delete(int id) { return execute(id, HttpMethod.DELETE, null, Void.class); }

    private <T> Mono<T> execute(int id, HttpMethod method, Object body, Class<T> responseType) {
        return Mono.defer(() -> {
            WebClient.RequestBodySpec request = client.method(method).uri("/{id}", id);
            WebClient.RequestHeadersSpec<?> headers = body == null ? request
                    : request.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
            return headers.exchangeToMono(response -> {
                int status = response.rawStatusCode();
                int expected = method == HttpMethod.DELETE ? 204 : 200;
                if (status != expected) {
                    return response.releaseBody().then(Mono.error(upstreamError(id, status)));
                }
                if (responseType == Void.class) return response.releaseBody().then(Mono.<T>empty());
                return response.bodyToMono(responseType)
                        .switchIfEmpty(Mono.error(new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE)));
            });
        }).timeout(Duration.ofSeconds(15))
                .onErrorMap(WebClientRequestException.class, failure ->
                        new ApiException(ErrorCode.STUDY_GROUPS_SERVICE_UNAVAILABLE, Map.of(), failure))
                .onErrorMap(this::isTimeout, failure ->
                        new ApiException(ErrorCode.STUDY_GROUPS_SERVICE_UNAVAILABLE, Map.of(), failure))
                .onErrorMap(failure -> !(failure instanceof ApiException), failure ->
                        new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE, Map.of(), failure));
    }

    private boolean isTimeout(Throwable failure) {
        if (failure instanceof ApiException) return false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof ReadTimeoutException) return true;
        }
        return false;
    }

    private ApiException upstreamError(int id, int status) {
        if (status == 404) return new ApiException(ErrorCode.STUDY_GROUP_NOT_FOUND, Map.of("id", Integer.toString(id)));
        ErrorCode code = status == 503 || status == 504
                ? ErrorCode.STUDY_GROUPS_SERVICE_UNAVAILABLE : ErrorCode.UPSTREAM_BAD_RESPONSE;
        return new ApiException(code, Map.of("upstreamStatus", Integer.toString(status)));
    }
}
