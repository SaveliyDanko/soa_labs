package ru.itmo.soa.isu;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;

import java.util.Map;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        int status = exception.getResponse().getStatus();
        String code = status == 404 ? "ENDPOINT_NOT_FOUND" : status == 405 ? "METHOD_NOT_ALLOWED" : "HTTP_ERROR";
        String message = status == 404 ? "Запрошенный URL не существует"
                : status == 405 ? "HTTP-метод не поддерживается для этого URL" : "Не удалось обработать HTTP-запрос";
        String path = requestPath();
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ApiErrorResponse(status, code, message, path, Map.of())).build();
    }

    private String requestPath() {
        if (uriInfo == null) return "";
        String path = uriInfo.getPath();
        return path.startsWith("/") ? path : "/" + path;
    }
}
