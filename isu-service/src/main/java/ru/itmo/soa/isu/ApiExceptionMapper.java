package ru.itmo.soa.isu;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ApiException exception) {
        String path = requestPath();
        ApiErrorResponse body = new ApiErrorResponse(exception.getStatus(), exception.getCode(),
                exception.getMessage(), path, exception.getDetails());
        return Response.status(exception.getStatus()).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
    }

    private String requestPath() {
        if (uriInfo == null) return "";
        String path = uriInfo.getPath();
        return path.startsWith("/") ? path : "/" + path;
    }
}
