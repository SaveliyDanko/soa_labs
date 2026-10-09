package ru.itmo.soa.studygroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "server.forward-headers-strategy=framework",
        "spring.datasource.url=jdbc:h2:mem:api;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.initialization-mode=always"
})
@AutoConfigureMockMvc
class StudyGroupApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private static final String BODY = "{\"name\":\"Math\",\"coordinates\":{\"x\":1.5,\"y\":-2},\"studentsCount\":25,"
            + "\"formOfEducation\":\"FULL_TIME_EDUCATION\",\"semesterEnum\":\"THIRD\","
            + "\"groupAdmin\":{\"name\":\"Anna\",\"nationality\":\"USA\","
            + "\"birthday\":\"1985-06-20T10:15:30+03:00[Europe/Moscow]\"}}";

    @BeforeEach
    void clear() { jdbc.update("DELETE FROM study_groups"); }

    @Test
    void preservesCrudAndLocationBehindTheGateway() throws Exception {
        var created = mvc.perform(post("/api/study-groups").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "localhost:3000"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", startsWith("https://localhost:3000/api/study-groups/")))
                .andReturn().getResponse();
        JsonNode group = json.readTree(created.getContentAsString());
        int id = group.get("id").asInt();
        mvc.perform(get("/api/study-groups/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Math")).andExpect(jsonPath("$.creationDate").isString());
        mvc.perform(put("/api/study-groups/" + id).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("25", "30")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentsCount").value(30))
                .andExpect(jsonPath("$.creationDate").value(group.get("creationDate").asText()));
        mvc.perform(delete("/api/study-groups/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/study-groups/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDY_GROUP_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/study-groups/" + id))
                .andExpect(header().string("Content-Language", "ru"));
    }

    @Test
    void preservesRepeatedSortAndFilterParameters() throws Exception {
        mvc.perform(post("/api/study-groups").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mvc.perform(post("/api/study-groups").contentType(MediaType.APPLICATION_JSON).content(BODY.replace("25", "30"))).andExpect(status().isCreated());
        mvc.perform(get("/api/study-groups").param("sort", "studentsCount,desc")
                        .param("filter", "name:contains:math", "studentsCount:ge:26").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].studentsCount").value(30));
        mvc.perform(get("/api/study-groups").param("sort", "name,asc", "studentsCount,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].studentsCount").value(30));
        mvc.perform(get("/api/study-groups/group-admin/max")).andExpect(status().isOk());
        mvc.perform(get("/api/study-groups/group-admin/count-greater").param("adminName", "A"))
                .andExpect(jsonPath("$.count").value(2));
        mvc.perform(get("/api/study-groups/name/contains").param("substring", "MATH"))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void preservesStrictJsonAndRussianValidation() throws Exception {
        for (String body : new String[]{"{", BODY.replace("\"THIRD\"", "\"UNKNOWN\""),
                BODY.replace("\"name\":\"Anna\"", "\"unexpected\":1,\"name\":\"Anna\"")}) {
            mvc.perform(post("/api/study-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
        }
        mvc.perform(post("/api/study-groups").contentType(MediaType.APPLICATION_JSON).content(BODY.replace("25", "0")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations.studentsCount").value("Значение должно быть больше нуля"));
        mvc.perform(post("/api/study-groups").contentType(MediaType.TEXT_PLAIN).content(BODY))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(patch("/api/study-groups")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }

    @Test
    void supportsCorsPreflightAndDocumentation() throws Exception {
        mvc.perform(options("/api/study-groups").header("Origin", "https://localhost:4000")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://localhost:4000"));
        mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isOk());
        mvc.perform(get("/webjars/swagger-ui/5.18.2/swagger-ui.css")).andExpect(status().isOk());
    }

    @Test
    void storesShareOneCollection() throws Exception {
        StudyGroupStore first = new StudyGroupStore(jdbc, json);
        StudyGroupStore second = new StudyGroupStore(jdbc, json);
        var request = json.readValue(BODY, ru.itmo.soa.model.ApiModels.StudyGroupRequest.class);
        var created = first.create(request);
        assertEquals("Math", second.find(created.getId()).getName());
        request.setName("Physics");
        second.update(created.getId(), request);
        assertEquals("Physics", first.find(created.getId()).getName());
        second.delete(created.getId());
        assertTrue(first.snapshot().isEmpty());
    }
}
