package ru.itmo.soa.isu;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.model.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;

class GatewayStudyGroupsClientTest {
    private static final AtomicInteger requests = new AtomicInteger();
    private static final DisposableServer server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
        requests.incrementAndGet();
        assertTrue(request.uri().startsWith("/api/study-groups/"));
        assertEquals("application/json", request.requestHeaders().get("Accept"));
        String id = request.uri().substring(request.uri().lastIndexOf('/') + 1);
        if (id.equals("404")) return response.status(404).send();
        if (id.equals("503")) return response.status(503).send();
        if (id.equals("500")) return response.status(500).send();
        if (id.equals("201")) return response.status(201).send();
        if (id.equals("204")) return response.status(200).send();
        if (id.equals("502")) return response.header("Content-Type", "application/json").sendString(Mono.just("{"));
        if (request.method().name().equals("DELETE")) return response.status(204).send();
        if (request.method().name().equals("PUT")) {
            return request.receive().aggregate().asString().flatMap(body -> {
                assertTrue(body.contains("EVENING_CLASSES"));
                return response.header("Content-Type", "application/json").sendString(Mono.just(
                        "{\"id\":7,\"name\":\"Math\",\"formOfEducation\":\"EVENING_CLASSES\"}")).then();
            });
        }
        return response.header("Content-Type", "application/json").sendString(Mono.just("{\"id\":7,\"name\":\"Math\"}"));
    }).bindNow();
    private GatewayStudyGroupsClient client;

    @BeforeEach void setup() {
        requests.set(0);
        client = new GatewayStudyGroupsClient(WebClient.builder(), "http://127.0.0.1:" + server.port());
    }
    @AfterAll static void close() { server.disposeNow(); }

    @Test void sendsRequestsLazilyThroughGateway() {
        Mono<Void> deletion = client.delete(7);
        assertEquals(0, requests.get());
        StepVerifier.create(deletion).verifyComplete();
        assertEquals(1, requests.get());
        StepVerifier.create(client.get(7)).assertNext(group -> assertEquals(7, group.getId())).verifyComplete();
        StudyGroupRequest request = new StudyGroupRequest();
        request.setFormOfEducation(FormOfEducation.EVENING_CLASSES);
        StepVerifier.create(client.update(7, request)).assertNext(group ->
                assertEquals(FormOfEducation.EVENING_CLASSES, group.getFormOfEducation())).verifyComplete();
    }

    @Test void preservesNotFoundAndUpstreamFailuresWithoutRetry() {
        expectError(404, "STUDY_GROUP_NOT_FOUND");
        expectError(503, "STUDY_GROUPS_SERVICE_UNAVAILABLE");
        expectError(500, "UPSTREAM_BAD_RESPONSE");
        assertEquals(3, requests.get());
    }

    @Test void rejectsUnexpectedStatusMissingBodyAndInvalidJson() {
        expectError(201, "UPSTREAM_BAD_RESPONSE");
        expectError(204, "UPSTREAM_BAD_RESPONSE");
        expectError(502, "UPSTREAM_BAD_RESPONSE");
    }

    @Test void reportsConnectionFailureInRussian() {
        var unavailable = new GatewayStudyGroupsClient(WebClient.builder(), "http://127.0.0.1:1");
        StepVerifier.create(unavailable.get(7)).expectErrorSatisfies(failure -> {
            assertInstanceOf(ApiException.class, failure);
            assertEquals("Сервис учебных групп временно недоступен", failure.getMessage());
            assertEquals(503, ((ApiException) failure).getStatus());
        }).verify(Duration.ofSeconds(6));
    }

    @Test void timesOutWithoutBlockingTheCaller() {
        var slow = new GatewayStudyGroupsClient(WebClient.builder().exchangeFunction(request -> Mono.never()),
                "http://gateway");
        StepVerifier.withVirtualTime(() -> slow.get(7)).thenAwait(Duration.ofSeconds(15))
                .expectErrorMatches(failure -> failure instanceof ApiException
                        && ((ApiException) failure).getCode().equals("STUDY_GROUPS_SERVICE_UNAVAILABLE"))
                .verify(Duration.ofSeconds(2));
    }

    private void expectError(int id, String code) {
        StepVerifier.create(client.get(id)).expectErrorSatisfies(failure -> {
            assertInstanceOf(ApiException.class, failure);
            assertEquals(code, ((ApiException) failure).getCode());
        }).verify(Duration.ofSeconds(5));
    }
}
