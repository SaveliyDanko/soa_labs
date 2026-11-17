package ru.itmo.soa.isu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import io.sentry.IScopes;
import reactor.core.publisher.Mono;
import ru.itmo.soa.web.ApiWebConfiguration;
import ru.itmo.soa.model.ApiModels.ActionResult;
import static org.mockito.Mockito.*;

@WebFluxTest(value = IsuController.class, properties = {"spring.cloud.config.enabled=false"})
@Import(ApiWebConfiguration.class)
class IsuApiTest {
    @Autowired WebTestClient client;
    @MockBean IsuService service;
    @MockBean IScopes scopes;

    @Test
    void keepsTheExpelContract() {
        when(service.expelAll(7)).thenReturn(Mono.just(new ActionResult("ALL_STUDENTS_EXPELLED", "Все студенты отчислены; пустая группа удалена", 7)));
        client.post().uri("/isu/group/7/expel-all").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.code").isEqualTo("ALL_STUDENTS_EXPELLED").jsonPath("$.resourceId").isEqualTo(7);
    }

    @Test
    void rejectsInvalidParameters() {
        client.post().uri("/isu/group/abc/expel-all").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_PARAMETER_TYPE");
        client.post().uri("/isu/group/0/expel-all").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_PARAMETER_VALUE");
        client.post().uri("/isu/group/7/change-edu-form/WRONG").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_PARAMETER_VALUE");
        verifyNoInteractions(service, scopes);
    }
}
