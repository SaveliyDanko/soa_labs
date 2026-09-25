package ru.itmo.soa.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper extends ErrorResponseMapper<ApiException> {
    @Override
    public Response toResponse(ApiException exception) {
        return respond(exception);
    }
}
