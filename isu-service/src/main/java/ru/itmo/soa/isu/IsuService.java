package ru.itmo.soa.isu;

import org.springframework.stereotype.Service;
import ru.itmo.soa.model.ApiModels.ActionResult;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

@Service
public class IsuService {
    private final StudyGroupsClient client;

    public IsuService(StudyGroupsClient client) { this.client = client; }

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
