package ru.itmo.soa.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UnexpectedExceptionMapper extends ErrorResponseMapper<Throwable> {
    @Override
    public Response toResponse(Throwable exception) {
        return internalError(exception);
    }
}
