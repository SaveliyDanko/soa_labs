package ru.itmo.soa.adapter;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.itmo.soa.model.ApiModels;

/** Compatibility facade: exactly one SOAP call per REST operation. */
@RestController
@RequestMapping(value = "/isu", produces = MediaType.APPLICATION_JSON_VALUE)
public class IsuRestController {
    private final IsuSoapClient soap;
    public IsuRestController(IsuSoapClient soap) { this.soap = soap; }

    @PostMapping("/group/{groupId}/expel-all")
    public Mono<ApiModels.ActionResult> expelAll(@PathVariable String groupId) { return soap.expelAll(groupId); }

    @PostMapping("/group/{groupId}/change-edu-form/{newForm}")
    public Mono<ApiModels.StudyGroup> changeEducationForm(@PathVariable String groupId, @PathVariable String newForm) {
        return soap.changeEducationForm(groupId, newForm);
    }
}
