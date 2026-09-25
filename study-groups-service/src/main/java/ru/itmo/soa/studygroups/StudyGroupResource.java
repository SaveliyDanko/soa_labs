package ru.itmo.soa.studygroups;

import jakarta.inject.Inject;
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
import ru.itmo.soa.error.RequestValidation;
import ru.itmo.soa.model.ApiModels.CountResult;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupPage;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import java.net.URI;
import java.util.List;

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
        RequestValidation.validate(request, validator);
        StudyGroup created = service.create(request);
        URI location = uriInfo.getAbsolutePathBuilder().path(created.getId().toString()).build();
        return Response.created(location).entity(created).build();
    }

    @GET
    public StudyGroupPage list(@QueryParam("page") String pageValue,
                               @QueryParam("size") String sizeValue,
                               @QueryParam("sort") List<String> sorts,
                               @QueryParam("filter") List<String> filters) {
        int page = RequestValidation.integer(pageValue, "page", 0, 0, Integer.MAX_VALUE);
        int size = RequestValidation.integer(sizeValue, "size", 20, 1, 100);
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
        RequestValidation.validate(request, validator);
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
        return service.countAdminGreaterThan(RequestValidation.requiredText(adminName, "adminName"));
    }

    @GET
    @Path("/name/contains")
    public List<StudyGroup> contains(@QueryParam("substring") String substring) {
        return service.nameContains(RequestValidation.requiredText(substring, "substring"));
    }

    private int id(String value) {
        return RequestValidation.positiveId(value, "id");
    }
}
