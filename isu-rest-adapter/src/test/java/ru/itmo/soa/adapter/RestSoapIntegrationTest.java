package ru.itmo.soa.adapter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestSoapIntegrationTest {
    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicReference<String> requestXml = new AtomicReference<>();
    static final AtomicReference<String> responseXml = new AtomicReference<>();
    static final AtomicInteger status = new AtomicInteger(200);
    static final DisposableServer upstream = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
        calls.incrementAndGet();
        assertEquals("/soap/isu", request.uri()); assertTrue(request.requestHeaders().get("SOAPAction").contains("urn:itmo:soa:isu:v1:"));
        return request.receive().aggregate().asString().flatMap(xml -> {
            requestXml.set(xml);
            return response.status(status.get()).header("Content-Type", "text/xml; charset=UTF-8")
                    .sendString(Mono.just(responseXml.get())).then();
        });
    }).bindNow();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("isu.soap-url", () -> "http://127.0.0.1:" + upstream.port() + "/soap/isu");
    }
    @LocalServerPort int port;
    WebTestClient client;
    @BeforeEach void prepare() {
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        calls.set(0); status.set(200);
        responseXml.set(envelope("<i:expelAllResponse><i:result><i:code>ALL_STUDENTS_EXPELLED</i:code>"
                + "<i:message>Все студенты отчислены; пустая группа удалена</i:message><i:resourceId>7</i:resourceId></i:result></i:expelAllResponse>"));
    }
    @AfterAll static void close() { upstream.disposeNow(); }

    @Test void preservesTheRestContractWithExactlyOneSoapCall() {
        client.post().uri("/isu/group/7/expel-all").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.code").isEqualTo("ALL_STUDENTS_EXPELLED").jsonPath("$.resourceId").isEqualTo(7);
        assertEquals(1, calls.get()); assertTrue(requestXml.get().contains("expelAll"));
    }

    @Test void delegatesValidationToSoapAndTranslatesTheFault() {
        status.set(500);
        responseXml.set(fault(400, "INVALID_PARAMETER_TYPE", "parameter", "groupId"));
        client.post().uri("/isu/group/abc/expel-all").exchange().expectStatus().isBadRequest()
                .expectHeader().valueEquals("Content-Language", "ru").expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_PARAMETER_TYPE").jsonPath("$.details.parameter").isEqualTo("groupId")
                .jsonPath("$.path").isEqualTo("/isu/group/abc/expel-all");
        assertEquals(1, calls.get()); assertTrue(requestXml.get().contains(">abc<"));
    }

    @Test void translatesNotFoundWithoutRepeatingTheSoapCall() {
        status.set(500); responseXml.set(fault(404, "STUDY_GROUP_NOT_FOUND", "id", "9999"));
        client.post().uri("/isu/group/9999/expel-all").exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("STUDY_GROUP_NOT_FOUND");
        assertEquals(1, calls.get());
    }

    @Test void preservesTypedGroupsIncludingDatesAndOptionalAdmin() {
        responseXml.set(envelope("<i:changeEducationFormResponse><i:result><i:id>7</i:id><i:creationDate>2025-01-01T00:00:00.123456Z</i:creationDate>"
                + "<i:name>Math</i:name><i:coordinates><i:x>1.5</i:x><i:y>-2</i:y></i:coordinates>"
                + "<i:studentsCount>25</i:studentsCount><i:formOfEducation>EVENING_CLASSES</i:formOfEducation>"
                + "</i:result></i:changeEducationFormResponse>"));
        client.post().uri("/isu/group/7/change-edu-form/EVENING_CLASSES").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(7).jsonPath("$.creationDate").isEqualTo("2025-01-01T00:00:00.123456Z")
                .jsonPath("$.formOfEducation").isEqualTo("EVENING_CLASSES").jsonPath("$.coordinates.x").isEqualTo(1.5);
        assertEquals(1, calls.get());
    }

    @Test void handlesEsbOutageWithoutRetrying() {
        status.set(503); responseXml.set("Service unavailable");
        client.post().uri("/isu/group/7/expel-all").exchange().expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("UPSTREAM_UNAVAILABLE");
        assertEquals(1, calls.get());
    }

    @Test void rejectsMalformedXmlExternalEntitiesAndEmptyResponses() {
        for (String xml : new String[]{"<html>failure</html>", "", "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>",
                envelope("<i:expelAllResponse/>"), envelope("<i:changeEducationFormResponse/>")}) {
            responseXml.set(xml);
            client.post().uri("/isu/group/7/expel-all").exchange().expectStatus().isEqualTo(502)
                    .expectBody().jsonPath("$.code").isEqualTo("UPSTREAM_BAD_RESPONSE");
        }
        assertEquals(5, calls.get());
    }

    static String envelope(String body) {
        return "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/' xmlns:i='urn:itmo:soa:isu:v1'><s:Body>" + body + "</s:Body></s:Envelope>";
    }
    static String fault(int status, String code, String key, String value) {
        return envelope("<s:Fault><faultcode>s:Client</faultcode><faultstring>Failure</faultstring><detail><i:IsuFault>"
                + "<i:status>" + status + "</i:status><i:code>" + code + "</i:code><i:message>Ошибка</i:message>"
                + "<i:detail><i:name>" + key + "</i:name><i:value>" + value + "</i:value></i:detail></i:IsuFault></detail></s:Fault>");
    }
}
