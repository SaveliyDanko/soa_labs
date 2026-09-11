package ru.itmo.soa.studygroups;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;
import ru.itmo.soa.model.ApiModels.ValidationErrorResponse;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ApiException exception) {
        Object body = exception.getViolations().isEmpty()
                ? new ApiErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(),
                        path(), exception.getDetails())
                : new ValidationErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(),
                        path(), exception.getViolations());
        return Response.status(exception.getStatus()).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
    }

    private String path() {
        return uriInfo == null ? "" : "/" + uriInfo.getPath();
    }
}
