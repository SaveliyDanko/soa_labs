package ru.itmo.soa.isu;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import ru.itmo.soa.error.ApiException;
import ru.itmo.soa.error.ErrorCode;
import ru.itmo.soa.error.RequestValidation;
import ru.itmo.soa.model.ApiModels.ActionResult;
import ru.itmo.soa.model.ApiModels.FormOfEducation;
import ru.itmo.soa.model.ApiModels.StudyGroup;

import java.util.Map;

@Path("/isu")
@Produces(MediaType.APPLICATION_JSON)
public class IsuResource {
    @Inject
    IsuService service;

    @POST
    @Path("/group/{groupId}/expel-all")
    public ActionResult expelAll(@PathParam("groupId") String groupId) {
        return service.expelAll(id(groupId));
    }

    @POST
    @Path("/group/{groupId}/change-edu-form/{newForm}")
    public StudyGroup changeEducationForm(@PathParam("groupId") String groupId,
                                          @PathParam("newForm") String newForm) {
        final FormOfEducation form;
        try {
            form = FormOfEducation.valueOf(newForm);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER_VALUE,
                    Map.of("parameter", "newForm", "value", String.valueOf(newForm)));
        }
        return service.changeEducationForm(id(groupId), form);
    }

    private int id(String value) {
        return RequestValidation.positiveId(value, "groupId");
    }
}
