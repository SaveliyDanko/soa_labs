package ru.itmo.soa.studygroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.itmo.soa.error.ValidationMessages;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "server.forward-headers-strategy=framework",
        "spring.r2dbc.url=r2dbc:h2:mem:///api;DB_CLOSE_DELAY=-1", "spring.sql.init.mode=always"
})
@AutoConfigureWebTestClient
class StudyGroupApiTest {
    @Autowired WebTestClient client;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired DatabaseClient database;
    private static final String BODY = "{\"name\":\"Math\",\"coordinates\":{\"x\":1.5,\"y\":-2},\"studentsCount\":25,"
            + "\"formOfEducation\":\"FULL_TIME_EDUCATION\",\"semesterEnum\":\"THIRD\","
            + "\"groupAdmin\":{\"name\":\"Anna\",\"nationality\":\"USA\","
            + "\"birthday\":\"1985-06-20T10:15:30+03:00[Europe/Moscow]\"}}";

    @BeforeEach
    void clear() {
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        database.sql("DELETE FROM study_groups").fetch().rowsUpdated().block();
    }

    private JsonNode create() throws Exception {
        byte[] body = client.post().uri("/api/study-groups").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isCreated().expectBody().returnResult().getResponseBody();
        return json.readTree(body);
    }

    @Test
    void preservesCrudAndLocationBehindTheGateway() throws Exception {
        var created = client.post().uri("/api/study-groups").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "localhost:3000")
                .exchange().expectStatus().isCreated().expectBody().returnResult();
        JsonNode group = json.readTree(created.getResponseBody());
        int id = group.get("id").asInt();
        assertEquals("https://localhost:3000/api/study-groups/" + id, created.getResponseHeaders().getFirst("Location"));
        client.get().uri("/api/study-groups/" + id).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Math").jsonPath("$.creationDate").isNotEmpty();
        client.put().uri("/api/study-groups/" + id).contentType(MediaType.APPLICATION_JSON).bodyValue(BODY.replace("25", "30"))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.studentsCount").isEqualTo(30)
                .jsonPath("$.creationDate").isEqualTo(group.get("creationDate").asText());
        client.delete().uri("/api/study-groups/" + id).exchange().expectStatus().isNoContent();
        client.get().uri("/api/study-groups/" + id).exchange().expectStatus().isNotFound()
                .expectHeader().valueEquals("Content-Language", "ru").expectBody()
                .jsonPath("$.code").isEqualTo("STUDY_GROUP_NOT_FOUND")
                .jsonPath("$.path").isEqualTo("/api/study-groups/" + id);
    }

    @Test
    void preservesRepeatedSortAndFilterParameters() throws Exception {
        create();
        client.post().uri("/api/study-groups").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY.replace("25", "30"))
                .exchange().expectStatus().isCreated();
        client.get().uri(uri -> uri.path("/api/study-groups").queryParam("sort", "studentsCount,desc")
                .queryParam("filter", "name:contains:math", "studentsCount:ge:26").queryParam("size", "1").build())
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].studentsCount").isEqualTo(30);
        client.get().uri(uri -> uri.path("/api/study-groups").queryParam("sort", "name,asc", "studentsCount,desc").build())
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.content[0].studentsCount").isEqualTo(30);
        client.get().uri("/api/study-groups/group-admin/max").exchange().expectStatus().isOk();
        client.get().uri("/api/study-groups/group-admin/count-greater?adminName=A").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.count").isEqualTo(2);
        client.get().uri("/api/study-groups/name/contains?substring=MATH").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    void preservesStrictJsonAndRussianValidation() {
        for (String body : new String[]{"{", BODY.replace("\"THIRD\"", "\"UNKNOWN\""),
                BODY.replace("\"name\":\"Anna\"", "\"unexpected\":1,\"name\":\"Anna\""),
                BODY.replace("25", "\"25\""), BODY.replace("25", "25.5")}) {
            client.post().uri("/api/study-groups").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                    .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("MALFORMED_JSON");
        }
        client.post().uri("/api/study-groups").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY.replace("25", "0"))
                .exchange().expectStatus().isEqualTo(422).expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.violations.studentsCount").isEqualTo("Значение должно быть больше нуля");
        client.post().uri("/api/study-groups").contentType(MediaType.TEXT_PLAIN).bodyValue(BODY)
                .exchange().expectStatus().isEqualTo(415).expectBody().jsonPath("$.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        client.patch().uri("/api/study-groups").exchange().expectStatus().isEqualTo(405).expectHeader().exists("Allow");
        client.get().uri("/missing").exchange().expectStatus().isNotFound().expectBody()
                .jsonPath("$.code").isEqualTo("ENDPOINT_NOT_FOUND");
    }

    @Test
    void validatesNestedFieldsOnCreateAndUpdate() throws Exception {
        int id = create().get("id").asInt();
        var invalid = json.readTree(BODY);
        ((ObjectNode) invalid.get("groupAdmin")).put("name", " ");
        for (var request : List.of(client.post().uri("/api/study-groups"), client.put().uri("/api/study-groups/" + id))) {
            request.contentType(MediaType.APPLICATION_JSON).bodyValue(json.writeValueAsString(invalid))
                    .exchange().expectStatus().isEqualTo(422).expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                    .jsonPath("$.violations['groupAdmin.name']").isEqualTo(ValidationMessages.NOT_BLANK);
        }
        client.get().uri("/api/study-groups/" + id).exchange().expectBody().jsonPath("$.groupAdmin.name").isEqualTo("Anna");
    }

    @Test
    void supportsCorsPreflightAndDocumentation() {
        client.options().uri("/api/study-groups").header("Origin", "https://localhost:4000")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Content-Type,sentry-trace,baggage")
                .exchange().expectStatus().isOk().expectHeader().valueEquals("Access-Control-Allow-Origin", "https://localhost:4000");
        for (String path : List.of("/openapi.yaml", "/swagger-ui.html", "/webjars/swagger-ui/5.18.2/swagger-ui.css")) {
            client.get().uri(path).exchange().expectStatus().isOk();
        }
    }

    @Test
    void storesShareOneCollection() throws Exception {
        StudyGroupStore first = new StudyGroupStore(database, json);
        StudyGroupStore second = new StudyGroupStore(database, json);
        var request = json.readValue(BODY, ru.itmo.soa.model.ApiModels.StudyGroupRequest.class);
        var created = first.create(request).block();
        assertEquals("Math", second.find(created.getId()).block().getName());
        request.setName("Physics");
        second.update(created.getId(), request).block();
        assertEquals("Physics", first.find(created.getId()).block().getName());
        second.delete(created.getId()).block();
        reactor.test.StepVerifier.create(first.snapshot()).verifyComplete();
    }
}
