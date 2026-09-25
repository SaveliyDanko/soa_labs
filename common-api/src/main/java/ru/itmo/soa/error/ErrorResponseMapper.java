package ru.itmo.soa.error;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class ErrorResponseMapper<T extends Throwable> implements ExceptionMapper<T> {
    private static final Logger LOGGER = Logger.getLogger(ErrorResponseMapper.class.getName());

    @Context
    UriInfo uriInfo;

    protected Response respond(ApiException exception) {
        return respond(exception, Response.status(exception.getStatus()));
    }

    protected Response internalError(Throwable exception) {
        LOGGER.log(Level.SEVERE, "Необработанная ошибка API", exception);
        return respond(new ApiException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    protected Response respond(ApiException exception, Response.ResponseBuilder response) {
        String path = uriInfo == null ? "/" : uriInfo.getRequestUri().getRawPath();
        return response.status(exception.getStatus()).type(MediaType.APPLICATION_JSON_TYPE)
                .language(Locale.forLanguageTag("ru")).entity(ErrorResponses.body(exception, path)).build();
    }
}
