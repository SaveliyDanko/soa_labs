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
        service = new StudyGroupService();
        service.store = new StudyGroupStore();
        service.query = new StudyGroupQuery();
    }

    @Test
    void supportsCrudAndKeepsGeneratedFields() {
        StudyGroup created = service.create(group("P3110", 25, "Anna"));
        assertEquals(1, created.getId());
        assertNotNull(created.getCreationDate());

        StudyGroup updated = service.update(created.getId(), group("P3110 updated", 30, "Anna"));
        assertEquals(created.getCreationDate(), updated.getCreationDate());
        assertEquals(30, updated.getStudentsCount());

        service.delete(created.getId());
        ApiException error = assertThrows(ApiException.class, () -> service.get(created.getId()));
        assertEquals(404, error.getStatus());
        assertEquals("STUDY_GROUP_NOT_FOUND", error.getCode());
    }

    @Test
    void combinesFilteringSortingPagingAndSpecialOperations() {
        service.create(group("Math A", 10, "Anna"));
        service.create(group("Math B", 30, "Zoe"));
        service.create(group("Physics", 20, "Mike"));

        StudyGroupPage page = service.list(0, 1, List.of("studentsCount,desc"),
                List.of("name:contains:math", "studentsCount:ge:10"));
        assertEquals(2, page.getTotalElements());
        assertEquals("Math B", page.getContent().get(0).getName());
        assertEquals("Zoe", service.maxAdmin().getGroupAdmin().getName());
        assertEquals(1, service.countAdminGreaterThan("Mike").getCount());
        assertEquals(2, service.nameContains("MATH").size());
    }

    @Test
    void rejectsUnknownQueryFieldsAndBadValues() {
        ApiException unknown = assertThrows(ApiException.class,
                () -> service.list(0, 20, List.of(), List.of("unknown:eq:x")));
        assertEquals("INVALID_FILTER_FIELD", unknown.getCode());

        ApiException badSort = assertThrows(ApiException.class,
                () -> service.list(0, 20, List.of("name,sideways"), List.of()));
        assertEquals("INVALID_SORT_FORMAT", badSort.getCode());
    }

    private StudyGroupRequest group(String name, long count, String admin) {
        return new StudyGroupRequest(name, new Coordinates(1.5f, -2f), count,
                FormOfEducation.FULL_TIME_EDUCATION, null,
                new Person(admin, null, null, Country.USA, null));
    }
}
