package ru.itmo.soa.isu;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOGGER = Logger.getLogger(UnexpectedExceptionMapper.class.getName());
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        LOGGER.log(Level.SEVERE, "Unhandled ISU API error", exception);
        String path = requestPath();
        return Response.status(500).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ApiErrorResponse(500, "INTERNAL_SERVER_ERROR", "Внутренняя ошибка сервера",
                        path, Map.of())).build();
    }

    private String requestPath() {
        if (uriInfo == null) return "";
        String path = uriInfo.getPath();
        return path.startsWith("/") ? path : "/" + path;
    }
}
