package ru.itmo.soa.studygroups;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import ru.itmo.soa.model.ApiModels.CountResult;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupPage;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/api/study-groups")
@Produces(MediaType.APPLICATION_JSON)
public class StudyGroupResource {
    @Inject
    StudyGroupService service;

    @Inject
    Validator validator;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(StudyGroupRequest request, @Context UriInfo uriInfo) {
        validate(request);
        StudyGroup created = service.create(request);
        URI location = uriInfo.getAbsolutePathBuilder().path(created.getId().toString()).build();
        return Response.created(location).entity(created).build();
    }

    @GET
    public StudyGroupPage list(@QueryParam("page") String pageValue,
                               @QueryParam("size") String sizeValue,
                               @QueryParam("sort") List<String> sorts,
                               @QueryParam("filter") List<String> filters) {
        int page = integerParameter(pageValue, "page", 0, 0, Integer.MAX_VALUE);
        int size = integerParameter(sizeValue, "size", 20, 1, 100);
        return service.list(page, size, sorts == null ? List.of() : sorts, filters == null ? List.of() : filters);
    }

    @GET
    @Path("/{id}")
    public StudyGroup get(@PathParam("id") String id) {
        return service.get(id(id));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public StudyGroup update(@PathParam("id") String id, StudyGroupRequest request) {
        validate(request);
        return service.update(id(id), request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        service.delete(id(id));
        return Response.noContent().build();
    }

    @GET
    @Path("/group-admin/max")
    public StudyGroup maxAdmin() {
        return service.maxAdmin();
    }

    @GET
    @Path("/group-admin/count-greater")
    public CountResult countGreater(@QueryParam("adminName") String adminName) {
        return service.countAdminGreaterThan(requiredText(adminName, "adminName"));
    }

    @GET
    @Path("/name/contains")
    public List<StudyGroup> contains(@QueryParam("substring") String substring) {
        return service.nameContains(requiredText(substring, "substring"));
    }

    private int id(String value) {
        return integerParameter(value, "id", null, 1, Integer.MAX_VALUE);
    }

    private int integerParameter(String value, String name, Integer defaultValue, int min, int max) {
        if (value == null) {
            if (defaultValue != null) return defaultValue;
            throw new ApiException(400, "MISSING_PARAMETER", "Отсутствует обязательный параметр '" + name + "'",
                    Map.of("parameter", name));
        }
        final int result;
        try {
            result = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(400, "INVALID_PARAMETER_TYPE", "Параметр '" + name + "' имеет неверный тип",
                    Map.of("parameter", name, "value", value, "expectedType", "int"));
        }
        if (result < min || result > max) {
            throw new ApiException(400, "INVALID_PARAMETER_VALUE", "Параметр '" + name + "' нарушает ограничения",
                    Map.of("parameter", name, "value", value));
        }
        return result;
    }

    private String requiredText(String value, String name) {
        if (value == null) {
            throw new ApiException(400, "MISSING_PARAMETER", "Отсутствует обязательный параметр '" + name + "'",
                    Map.of("parameter", name));
        }
        if (value.isBlank()) {
            throw new ApiException(400, "INVALID_PARAMETER_VALUE", "Параметр '" + name + "' не может быть пустым",
                    Map.of("parameter", name, "value", value));
        }
        return value;
    }

    private void validate(StudyGroupRequest request) {
        if (request == null) {
            throw ApiException.validation(Map.of("request", "must not be null"));
        }
        Set<ConstraintViolation<StudyGroupRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            violations.stream().sorted((a, b) -> a.getPropertyPath().toString()
                            .compareTo(b.getPropertyPath().toString()))
                    .forEach(violation -> fields.putIfAbsent(
                            violation.getPropertyPath().toString(), violation.getMessage()));
            throw ApiException.validation(fields);
        }
    }
}
