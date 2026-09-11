package ru.itmo.soa.isu;

import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

public interface StudyGroupsClient {
    StudyGroup get(int id);
    StudyGroup update(int id, StudyGroupRequest request);
    void delete(int id);
}
