package ru.itmo.soa.isu;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.itmo.soa.model.ApiModels.ActionResult;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

@ApplicationScoped
public class IsuService {
    @Inject
    StudyGroupsClient client;

    public ActionResult expelAll(int groupId) {
        client.delete(groupId);
        return new ActionResult("ALL_STUDENTS_EXPELLED",
                "Все студенты отчислены; пустая группа удалена", groupId);
    }

    public StudyGroup changeEducationForm(int groupId, FormOfEducation newForm) {
        StudyGroup current = client.get(groupId);
        StudyGroupRequest request = current.toRequest();
        request.setFormOfEducation(newForm);
        return client.update(groupId, request);
    }
}
