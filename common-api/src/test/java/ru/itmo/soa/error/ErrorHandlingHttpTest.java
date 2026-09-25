package ru.itmo.soa.error;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorHandlingHttpTest {
    private final JerseyTest server = new JerseyTest(new InMemoryTestContainerFactory()) {
        @Override
        protected Application configure() {
            return new ApiApplication(ErrorResource.class) {};
        }
    };

    @BeforeEach
    void start() throws Exception { server.setUp(); }

    @AfterEach
    void stop() throws Exception { server.tearDown(); }

    @ParameterizedTest
    @CsvSource({"domain, 404, STUDY_GROUP_NOT_FOUND", "validation, 422, VALIDATION_FAILED",
            "http, 405, METHOD_NOT_ALLOWED", "unexpected, 500, INTERNAL_SERVER_ERROR"})
    void registersMappersInjectsThePathAndSerializesJson(String error, int status, String code) {
        try (Response response = server.target("errors/" + error).request().get()) {
            assertEquals(status, response.getStatus());
            assertEquals("application/json", response.getMediaType().toString());
            assertEquals("ru", response.getLanguage().getLanguage());
            try (var reader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
                JsonObject body = reader.readObject();
                assertEquals(code, body.getString("code"));
                assertEquals(status, body.getInt("status"));
                assertEquals("/errors/" + error, body.getString("path"));
                assertFalse(body.getString("message").contains("secret"));
                if (error.equals("validation")) {
                    assertEquals(ValidationMessages.POSITIVE,
                            body.getJsonObject("violations").getString("studentsCount"));
                }
                if (error.equals("http")) assertTrue(response.getAllowedMethods().contains("GET"));
            }
        }
    }

    @Test
    void normalizesContainerGeneratedErrors() {
        try (Response response = server.target("missing").request().get()) {
            assertEquals(404, response.getStatus());
            assertEquals("ru", response.getLanguage().getLanguage());
            assertTrue(response.readEntity(String.class).contains("ENDPOINT_NOT_FOUND"));
        }
        try (Response response = server.target("errors/domain").request().put(Entity.text(""))) {
            assertEquals(405, response.getStatus());
            assertTrue(response.getAllowedMethods().contains("GET"));
            assertTrue(response.readEntity(String.class).contains("METHOD_NOT_ALLOWED"));
        }
    }

    @Path("/errors")
    @Produces(MediaType.APPLICATION_JSON)
    public static class ErrorResource {
        @GET
        @Path("/{kind}")
        public String get(@PathParam("kind") String kind) {
            throw switch (kind) {
                case "domain" -> new ApiException(ErrorCode.STUDY_GROUP_NOT_FOUND, Map.of("id", "7"));
                case "validation" -> ApiException.validation(Map.of("studentsCount", ValidationMessages.POSITIVE));
                case "http" -> new WebApplicationException(Response.status(405).allow("GET").entity("secret").build());
                default -> new IllegalStateException("secret");
            };
        }
    }
}
