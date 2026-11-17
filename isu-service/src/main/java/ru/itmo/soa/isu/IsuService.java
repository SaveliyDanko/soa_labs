package ru.itmo.soa.isu;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.itmo.soa.model.ApiModels.ActionResult;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

@Service
public class IsuService {
    private final StudyGroupsClient client;

    public IsuService(StudyGroupsClient client) {
        this.client = client;
    }

    public Mono<ActionResult> expelAll(int groupId) {
        return client.delete(groupId).thenReturn(new ActionResult("ALL_STUDENTS_EXPELLED",
                "Все студенты отчислены; пустая группа удалена", groupId));
    }

    public Mono<StudyGroup> changeEducationForm(int groupId, FormOfEducation newForm) {
        return client.get(groupId).flatMap(current -> {
            StudyGroupRequest request = current.toRequest();
            request.setFormOfEducation(newForm);
            return client.update(groupId, request);
        });
    }
}
