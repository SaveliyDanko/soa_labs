package ru.itmo.soa.isu;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
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
            throw new ApiException(400, "INVALID_PARAMETER_VALUE",
                    "Параметр 'newForm' содержит неизвестную форму обучения",
                    Map.of("parameter", "newForm", "value", String.valueOf(newForm)));
        }
        return service.changeEducationForm(id(groupId), form);
    }

    private int id(String value) {
        final int id;
        try {
            id = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(400, "INVALID_PARAMETER_TYPE", "Параметр 'groupId' имеет неверный тип",
                    Map.of("parameter", "groupId", "value", value, "expectedType", "int"));
        }
        if (id < 1) {
            throw new ApiException(400, "INVALID_PARAMETER_VALUE", "Параметр 'groupId' должен быть больше 0",
                    Map.of("parameter", "groupId", "value", value));
        }
        return id;
    }
}
