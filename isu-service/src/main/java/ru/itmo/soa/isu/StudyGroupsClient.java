package ru.itmo.soa.isu;

import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import reactor.core.publisher.Mono;

public interface StudyGroupsClient {
    Mono<StudyGroup> get(int id);
    Mono<StudyGroup> update(int id, StudyGroupRequest request);
    Mono<Void> delete(int id);
}
