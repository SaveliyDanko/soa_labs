package ru.itmo.soa.isu;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.error.RequestValidation;
import ru.itmo.soa.model.ApiModels.*;
import java.util.Map;

@RestController
@RequestMapping(value = "/isu", produces = MediaType.APPLICATION_JSON_VALUE)
public class IsuResource {
    private final IsuService service;

    public IsuResource(IsuService service) { this.service = service; }

    @PostMapping("/group/{groupId}/expel-all")
    public ActionResult expelAll(@PathVariable String groupId) {
        return service.expelAll(RequestValidation.positiveId(groupId, "groupId"));
    }

    @PostMapping("/group/{groupId}/change-edu-form/{newForm}")
    public StudyGroup changeEducationForm(@PathVariable String groupId, @PathVariable String newForm) {
        final FormOfEducation form;
        try {
            form = FormOfEducation.valueOf(newForm);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER_VALUE, Map.of("parameter", "newForm", "value", newForm));
        }
        return service.changeEducationForm(RequestValidation.positiveId(groupId, "groupId"), form);
    }
}
