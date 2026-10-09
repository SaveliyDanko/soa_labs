package ru.itmo.soa.studygroups;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.itmo.soa.error.RequestValidation;
import ru.itmo.soa.model.ApiModels.*;
import javax.validation.Validator;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/api/study-groups", produces = MediaType.APPLICATION_JSON_VALUE)
public class StudyGroupResource {
    private final StudyGroupService service;
    private final Validator validator;

    public StudyGroupResource(StudyGroupService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudyGroup> create(@RequestBody StudyGroupRequest request) {
        RequestValidation.validate(request, validator);
        StudyGroup created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public StudyGroupPage list(@RequestParam org.springframework.util.MultiValueMap<String, String> parameters) {
        String pageValue = parameters.getFirst("page");
        String sizeValue = parameters.getFirst("size");
        List<String> sorts = parameters.get("sort");
        List<String> filters = parameters.get("filter");
        int page = RequestValidation.integer(pageValue, "page", 0, 0, Integer.MAX_VALUE);
        int size = RequestValidation.integer(sizeValue, "size", 20, 1, 100);
        return service.list(page, size, sorts == null ? List.of() : sorts, filters == null ? List.of() : filters);
    }

    @GetMapping("/{id}")
    public StudyGroup get(@PathVariable String id) { return service.get(id(id)); }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StudyGroup update(@PathVariable String id, @RequestBody StudyGroupRequest request) {
        RequestValidation.validate(request, validator);
        return service.update(id(id), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/group-admin/max")
    public StudyGroup maxAdmin() { return service.maxAdmin(); }

    @GetMapping("/group-admin/count-greater")
    public CountResult countGreater(@RequestParam(required = false) String adminName) {
        return service.countAdminGreaterThan(RequestValidation.requiredText(adminName, "adminName"));
    }

    @GetMapping("/name/contains")
    public List<StudyGroup> contains(@RequestParam(required = false) String substring) {
        return service.nameContains(RequestValidation.requiredText(substring, "substring"));
    }

    private int id(String value) { return RequestValidation.positiveId(value, "id"); }
}
