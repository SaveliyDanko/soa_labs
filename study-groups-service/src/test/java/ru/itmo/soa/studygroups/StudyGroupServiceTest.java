package ru.itmo.soa.studygroups;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.model.ApiModels.Coordinates;
import ru.itmo.soa.model.ApiModels.Country;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.model.ApiModels.Person;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupPage;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudyGroupServiceTest {
    private StudyGroupService service;
    @BeforeEach
    void setUp() {
        var connection = io.r2dbc.spi.ConnectionFactories.get("r2dbc:h2:mem:///service-" + java.util.UUID.randomUUID()
                + "?options=DB_CLOSE_DELAY=-1");
        var initializer = new org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connection);
        initializer.setDatabasePopulator(new org.springframework.r2dbc.connection.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("schema.sql")));
        initializer.afterPropertiesSet();
        StudyGroupStore store = new StudyGroupStore(org.springframework.r2dbc.core.DatabaseClient.create(connection),
                new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
        service = new StudyGroupService(store, new StudyGroupQuery());
    }

    @Test
    void supportsCrudAndKeepsGeneratedFields() {
        StudyGroup created = service.create(group("P3110", 25, "Anna")).block();
        assertEquals(1, created.getId());
        assertNotNull(created.getCreationDate());

        StudyGroup updated = service.update(created.getId(), group("P3110 updated", 30, "Anna")).block();
        assertEquals(created.getCreationDate(), updated.getCreationDate());
        assertEquals(30, updated.getStudentsCount());

        service.delete(created.getId()).block();
        ApiException error = assertThrows(ApiException.class, () -> service.get(created.getId()).block());
        assertEquals(404, error.getStatus());
        assertEquals("STUDY_GROUP_NOT_FOUND", error.getCode());
    }

    @Test
    void combinesFilteringSortingPagingAndSpecialOperations() {
        service.create(group("Math A", 10, "Anna")).block();
        service.create(group("Math B", 30, "Zoe")).block();
        service.create(group("Physics", 20, "Mike")).block();

        StudyGroupPage page = service.list(0, 1, List.of("studentsCount,desc"),
                List.of("name:contains:math", "studentsCount:ge:10")).block();
        assertEquals(2, page.getTotalElements());
        assertEquals("Math B", page.getContent().get(0).getName());
        assertEquals("Zoe", service.maxAdmin().block().getGroupAdmin().getName());
        assertEquals(1, service.countAdminGreaterThan("Mike").block().getCount());
        assertEquals(2, service.nameContains("MATH").collectList().block().size());
    }

    @Test
    void rejectsUnknownQueryFieldsAndBadValues() {
        ApiException unknown = assertThrows(ApiException.class,
                () -> service.list(0, 20, List.of(), List.of("unknown:eq:x")).block());
        assertEquals("INVALID_FILTER_FIELD", unknown.getCode());

        ApiException badSort = assertThrows(ApiException.class,
                () -> service.list(0, 20, List.of("name,sideways"), List.of()).block());
        assertEquals("INVALID_SORT_FORMAT", badSort.getCode());
    }

    @Test
    void defersWritesUntilSubscriptionAndPropagatesMissingGroups() {
        var pending = service.create(group("Lazy", 10, "Anna"));
        reactor.test.StepVerifier.create(service.nameContains("Lazy")).verifyComplete();
        reactor.test.StepVerifier.create(pending).assertNext(group -> assertEquals("Lazy", group.getName())).verifyComplete();
        reactor.test.StepVerifier.create(service.update(9999, group("Missing", 10, "Anna")))
                .expectErrorMatches(error -> error instanceof ApiException && ((ApiException) error).getStatus() == 404)
                .verify();
        reactor.test.StepVerifier.create(service.delete(9999))
                .expectErrorMatches(error -> error instanceof ApiException && ((ApiException) error).getStatus() == 404)
                .verify();
    }

    private StudyGroupRequest group(String name, long count, String admin) {
        return new StudyGroupRequest(name, new Coordinates(1.5f, -2f), count,
                FormOfEducation.FULL_TIME_EDUCATION, null,
                new Person(admin, null, null, Country.USA, null));
    }
}
