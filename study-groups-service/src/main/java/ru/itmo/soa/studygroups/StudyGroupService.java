package ru.itmo.soa.studygroups;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.CountResult;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupPage;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class StudyGroupService {
    @Inject
    StudyGroupStore store;

    @Inject
    StudyGroupQuery query;

    public StudyGroup create(StudyGroupRequest request) { return store.create(request); }
    public StudyGroup get(int id) { return store.find(id); }
    public StudyGroup update(int id, StudyGroupRequest request) { return store.update(id, request); }
    public void delete(int id) { store.delete(id); }

    public StudyGroupPage list(int page, int size, List<String> sorts, List<String> filters) {
        List<StudyGroup> selected = query.apply(store.snapshot(), filters, sorts);
        long offset = (long) page * size;
        int from = (int) Math.min(offset, selected.size());
        int to = Math.min(from + size, selected.size());
        long total = selected.size();
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new StudyGroupPage(selected.subList(from, to), page, size, total, totalPages);
    }

    public StudyGroup maxAdmin() {
        return store.snapshot().stream()
                .filter(group -> group.getGroupAdmin() != null)
                .max(Comparator.comparing((StudyGroup group) -> group.getGroupAdmin().getName())
                        .thenComparing(group -> -group.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NO_GROUP_WITH_ADMIN));
    }

    public CountResult countAdminGreaterThan(String adminName) {
        long count = store.snapshot().stream()
                .filter(group -> group.getGroupAdmin() != null)
                .filter(group -> group.getGroupAdmin().getName().compareTo(adminName) > 0)
                .count();
        return new CountResult(count, "groupAdmin.name", adminName);
    }

    public List<StudyGroup> nameContains(String substring) {
        String needle = substring.toLowerCase(Locale.ROOT);
        return store.snapshot().stream()
                .filter(group -> group.getName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(StudyGroup::getId))
                .toList();
    }
}
