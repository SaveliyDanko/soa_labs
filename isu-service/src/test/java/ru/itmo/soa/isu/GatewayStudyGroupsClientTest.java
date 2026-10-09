package ru.itmo.soa.isu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import ru.itmo.soa.error.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GatewayStudyGroupsClientTest {
    private MockRestServiceServer server;
    private GatewayStudyGroupsClient client;
    @BeforeEach
    void setUp() {
        client = new GatewayStudyGroupsClient(new RestTemplateBuilder().additionalCustomizers(
                template -> server = MockRestServiceServer.bindTo(template).build()), "http://zuul:8080");
    }

    @Test
    void sendsRequestsThroughGateway() {
        server.expect(requestTo("http://zuul:8080/api/study-groups/7")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());
        client.delete(7);
        server.verify();
    }

    @Test
    void preservesNotFoundAndUpstreamFailures() {
        server.expect(requestTo("http://zuul:8080/api/study-groups/7")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertEquals("STUDY_GROUP_NOT_FOUND", assertThrows(ApiException.class, () -> client.get(7)).getCode());
        server.verify();
    }

    @Test
    void reportsGatewayUnavailableInRussian() {
        server.expect(requestTo("http://zuul:8080/api/study-groups/7")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        ApiException exception = assertThrows(ApiException.class, () -> client.get(7));
        assertEquals(503, exception.getStatus());
        assertEquals("Сервис учебных групп временно недоступен", exception.getMessage());
        server.verify();
    }
}
