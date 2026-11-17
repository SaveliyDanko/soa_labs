package ru.itmo.soa.isu;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.itmo.soa.model.ApiModels.ActionResult;
import ru.itmo.soa.model.ApiModels.Coordinates;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsuServiceTest {
    @Test
    void invokesFirstServiceForBothCompositeOperations() {
        FakeClient client = new FakeClient();
        IsuService service = new IsuService(client);

        StudyGroup updated = service.changeEducationForm(7, FormOfEducation.EVENING_CLASSES).block();
        assertEquals(FormOfEducation.EVENING_CLASSES, updated.getFormOfEducation());
        assertEquals(7, client.updatedId);

        ActionResult result = service.expelAll(7).block();
        assertTrue(client.deleted);
        assertEquals("ALL_STUDENTS_EXPELLED", result.getCode());
    }

    private static class FakeClient implements StudyGroupsClient {
        private final StudyGroup group = new StudyGroup(7, Instant.now(), new StudyGroupRequest(
                "P3110", new Coordinates(1f, 2f), 20L, FormOfEducation.FULL_TIME_EDUCATION, null, null));
        private int updatedId;
        private boolean deleted;

        @Override public Mono<StudyGroup> get(int id) { return Mono.fromSupplier(group::copy); }
        @Override public Mono<StudyGroup> update(int id, StudyGroupRequest request) {
            return Mono.fromSupplier(() -> {
                updatedId = id;
                group.updateFrom(request);
                return group.copy();
            });
        }
        @Override public Mono<Void> delete(int id) { return Mono.fromRunnable(() -> deleted = true); }
    }
}
