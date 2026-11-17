package ru.itmo.soa.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.gateway.routes[0].id=upstream", "spring.cloud.gateway.routes[0].uri=${test.upstream}", "spring.cloud.gateway.routes[0].predicates[0]=Path=/isu/**,/soap/**",
        "spring.cloud.gateway.routes[1].id=unavailable", "spring.cloud.gateway.routes[1].predicates[0]=Path=/unavailable/**",
        "spring.cloud.gateway.routes[1].uri=http://127.0.0.1:1"
})
@AutoConfigureWebTestClient
class GatewayErrorTest {
    static final DisposableServer upstream = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
        if (request.uri().equals("/soap/isu")) {
            return response.status(500).header("Content-Type", "text/xml; charset=UTF-8")
                    .sendString(Mono.just("<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><s:Fault>"
                            + "<faultcode>s:Client</faultcode><faultstring>Validation failed</faultstring></s:Fault></s:Body></s:Envelope>"));
        }
        if (request.uri().equals("/isu/html")) {
            return response.status(503).header("Content-Type", "text/html").header("Content-Length", "19")
                    .sendString(Mono.just("<html>failed</html>"));
        }
        if (request.uri().equals("/isu/json")) {
            return response.status(500).header("Content-Type", "application/json")
                    .sendString(Mono.just("{\"code\":\"INTERNAL_SERVER_ERROR\"}"));
        }
        return response.header("Content-Type", "application/json").sendString(Mono.just("{\"ok\":true}"));
    }).bindNow();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("test.upstream", () -> "http://127.0.0.1:" + upstream.port());
    }
    @Autowired WebTestClient client;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @org.junit.jupiter.api.BeforeEach void client() {
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }
    @AfterAll static void close() { upstream.disposeNow(); }

    @Test void connectionErrorsUseRussianJson() {
        client.post().uri("/unavailable/group/1").exchange().expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Content-Language", "ru")
                .expectHeader().valueEquals("X-Gateway", "spring-cloud-gateway").expectBody()
                .jsonPath("$.code").isEqualTo("UPSTREAM_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("Сервис временно недоступен")
                .jsonPath("$.path").isEqualTo("/unavailable/group/1");
    }

    @Test void haproxyHtmlErrorsUseTheSameContract() {
        client.get().uri("/isu/html").exchange().expectStatus().isEqualTo(503)
                .expectHeader().contentType("application/json").expectBody()
                .jsonPath("$.code").isEqualTo("UPSTREAM_UNAVAILABLE").jsonPath("$.path").isEqualTo("/isu/html");
    }

    @Test void preservesErrorsFromApplicationServices() {
        client.get().uri("/isu/json").exchange().expectStatus().isEqualTo(500)
                .expectBody().json("{\"code\":\"INTERNAL_SERVER_ERROR\"}");
    }

    @Test void preservesSoapFaultStatusAndXmlBody() {
        client.post().uri("/soap/isu").exchange().expectStatus().isEqualTo(500)
                .expectHeader().contentTypeCompatibleWith("text/xml").expectBody(String.class)
                .value(xml -> org.junit.jupiter.api.Assertions.assertTrue(xml.contains("<s:Fault>")));
    }

    @Test void routesRequestsAndHandlesUnmatchedPaths() {
        client.get().uri("/isu/ok").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Gateway", "spring-cloud-gateway").expectBody().jsonPath("$.ok").isEqualTo(true);
        client.get().uri("/missing").exchange().expectStatus().isNotFound().expectBody()
                .jsonPath("$.code").isEqualTo("ENDPOINT_NOT_FOUND");
    }
}
