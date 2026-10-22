package ru.itmo.soa.studygroups;

import java.net.URI;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.itmo.soa.error.RequestValidation;
import ru.itmo.soa.model.ApiModels.*;

@RestController
@RequestMapping(value = "/api/study-groups", produces = MediaType.APPLICATION_JSON_VALUE)
public class StudyGroupResource {
    private final StudyGroupService service;
    public StudyGroupResource(StudyGroupService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudyGroup> create(@Valid @RequestBody StudyGroupRequest request) {
        StudyGroup created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public StudyGroupPage list(@RequestParam MultiValueMap<String, String> parameters) {
        String pageValue = parameters.getFirst("page");
        String sizeValue = parameters.getFirst("size");
        List<String> sorts = parameters.getOrDefault("sort", List.of());
        List<String> filters = parameters.getOrDefault("filter", List.of());
        int page = RequestValidation.integer(pageValue, "page", 0, 0, Integer.MAX_VALUE);
        int size = RequestValidation.integer(sizeValue, "size", 20, 1, 100);
        return service.list(page, size, sorts, filters);
    }

    @GetMapping("/{id}")
    public StudyGroup get(@PathVariable String id) {
        return service.get(id(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StudyGroup update(@PathVariable String id, @Valid @RequestBody StudyGroupRequest request) {
        return service.update(id(id), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/group-admin/max")
    public StudyGroup maxAdmin() {
        return service.maxAdmin();
    }

    @GetMapping("/group-admin/count-greater")
    public CountResult countGreater(@RequestParam(required = false) String adminName) {
        return service.countAdminGreaterThan(RequestValidation.requiredText(adminName, "adminName"));
    }

    @GetMapping("/name/contains")
    public List<StudyGroup> contains(@RequestParam(required = false) String substring) {
        return service.nameContains(RequestValidation.requiredText(substring, "substring"));
    }

    private int id(String value) {
        return RequestValidation.positiveId(value, "id");
    }
}
