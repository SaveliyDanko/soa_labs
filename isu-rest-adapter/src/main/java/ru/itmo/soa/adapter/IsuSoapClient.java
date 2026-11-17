package ru.itmo.soa.adapter;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels;
import ru.itmo.soa.soap.*;

@Component
public class IsuSoapClient {
    private final WebClient http;
    private final SoapCodec codec;

    public IsuSoapClient(WebClient.Builder builder, SoapCodec codec, @Value("${isu.soap-url}") String url) {
        this.codec = codec;
        this.http = builder.clientConnector(new ReactorClientHttpConnector(HttpClient.create().disableRetry(true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000).responseTimeout(Duration.ofSeconds(40))))
                .baseUrl(url).codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)).build();
    }

    public Mono<ApiModels.ActionResult> expelAll(String groupId) {
        ExpelAll request = new ExpelAll(); request.setGroupId(groupId);
        return call("expelAll", request, ExpelAllResponse.class).map(response -> SoapModels.toRest(response.getResult()))
                .onErrorMap(failure -> !(failure instanceof ApiException), this::badResponse);
    }

    public Mono<ApiModels.StudyGroup> changeEducationForm(String groupId, String newForm) {
        ChangeEducationForm request = new ChangeEducationForm(); request.setGroupId(groupId); request.setNewForm(newForm);
        return call("changeEducationForm", request, ChangeEducationFormResponse.class).map(response -> SoapModels.toRest(response.getResult()))
                .onErrorMap(failure -> !(failure instanceof ApiException), this::badResponse);
    }

    private <T> Mono<T> call(String action, Object payload, Class<T> type) {
        return Mono.defer(() -> http.post().contentType(MediaType.TEXT_XML).accept(MediaType.TEXT_XML)
                .header("SOAPAction", "\"" + SoapCodec.ISU + ":" + action + "\"").bodyValue(codec.request(payload))
                .exchangeToMono(response -> {
                    int status = response.rawStatusCode();
                    if (status == 502 || status == 503 || status == 504) {
                        return response.releaseBody().then(Mono.error(new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE)));
                    }
                    if (status != 200 && status != 500) return response.releaseBody()
                            .then(Mono.error(new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE)));
                    return response.bodyToMono(byte[].class).switchIfEmpty(Mono.error(new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE)))
                            .map(bytes -> codec.response(bytes, type));
                })).timeout(Duration.ofSeconds(45))
                .onErrorMap(failure -> failure instanceof WebClientRequestException || failure instanceof java.util.concurrent.TimeoutException,
                        failure -> new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, Map.of(), failure))
                .onErrorMap(failure -> !(failure instanceof ApiException),
                        this::badResponse);
    }

    private ApiException badResponse(Throwable failure) { return new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE, Map.of(), failure); }
}
