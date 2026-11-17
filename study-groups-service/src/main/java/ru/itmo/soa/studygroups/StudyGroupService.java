package ru.itmo.soa.studygroups;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.model.ApiModels.*;

@Service
public class StudyGroupService {
    private final StudyGroupStore store;
    private final StudyGroupQuery query;

    public StudyGroupService(StudyGroupStore store, StudyGroupQuery query) {
        this.store = store;
        this.query = query;
    }

    public Mono<StudyGroup> create(StudyGroupRequest request) { return store.create(request); }
    public Mono<StudyGroup> get(int id) { return store.find(id); }
    public Mono<StudyGroup> update(int id, StudyGroupRequest request) { return store.update(id, request); }
    public Mono<Void> delete(int id) { return store.delete(id); }

    public Mono<StudyGroupPage> list(int page, int size, List<String> sorts, List<String> filters) {
        return store.snapshot().collectList().map(groups -> {
            List<StudyGroup> selected = query.apply(groups, filters, sorts);
            long offset = (long) page * size;
            int from = (int) Math.min(offset, selected.size());
            int to = Math.min(from + size, selected.size());
            long total = selected.size();
            int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
            return new StudyGroupPage(selected.subList(from, to), page, size, total, totalPages);
        });
    }

    public Mono<StudyGroup> maxAdmin() {
        Comparator<StudyGroup> comparator = Comparator.comparing((StudyGroup group) -> group.getGroupAdmin().getName())
                .thenComparing(StudyGroup::getId, Comparator.reverseOrder());
        return store.snapshot().filter(group -> group.getGroupAdmin() != null)
                .reduce((first, second) -> comparator.compare(first, second) >= 0 ? first : second)
                .switchIfEmpty(Mono.error(new ApiException(ErrorCode.NO_GROUP_WITH_ADMIN)));
    }

    public Mono<CountResult> countAdminGreaterThan(String adminName) {
        return store.snapshot().filter(group -> group.getGroupAdmin() != null)
                .filter(group -> group.getGroupAdmin().getName().compareTo(adminName) > 0)
                .count().map(count -> new CountResult(count, "groupAdmin.name", adminName));
    }

    public Flux<StudyGroup> nameContains(String substring) {
        String needle = substring.toLowerCase(Locale.ROOT);
        return store.snapshot().filter(group -> group.getName().toLowerCase(Locale.ROOT).contains(needle));
    }
}
