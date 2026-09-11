package ru.itmo.soa.studygroups;

import jakarta.enterprise.context.ApplicationScoped;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** Thread-safe collection storage. IDs and creation timestamps are always server-generated. */
@ApplicationScoped
public class StudyGroupStore {
    private final Map<Integer, StudyGroup> groups = new LinkedHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public synchronized StudyGroup create(StudyGroupRequest request) {
        int id = sequence.incrementAndGet();
        StudyGroup group = new StudyGroup(id, Instant.now(), request);
        groups.put(id, group);
        return group.copy();
    }

    public synchronized StudyGroup find(int id) {
        StudyGroup group = groups.get(id);
        if (group == null) {
            throw notFound(id);
        }
        return group.copy();
    }

    public synchronized StudyGroup update(int id, StudyGroupRequest request) {
        StudyGroup group = groups.get(id);
        if (group == null) {
            throw notFound(id);
        }
        group.updateFrom(request);
        return group.copy();
    }

    public synchronized void delete(int id) {
        if (groups.remove(id) == null) {
            throw notFound(id);
        }
    }

    public synchronized List<StudyGroup> snapshot() {
        return groups.values().stream().map(StudyGroup::copy)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ApiException notFound(int id) {
        return new ApiException(404, "STUDY_GROUP_NOT_FOUND",
                "Учебная группа с id=" + id + " не найдена", Map.of("id", Integer.toString(id)));
    }
}
