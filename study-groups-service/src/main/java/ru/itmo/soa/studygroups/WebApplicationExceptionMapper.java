package ru.itmo.soa.studygroups;

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
        String code;
        String message;
        Map<String, String> details = Map.of();
        switch (status) {
            case 400 -> { code = "MALFORMED_JSON"; message = "Тело запроса содержит некорректный JSON или недопустимое значение enum"; }
            case 404 -> { code = "ENDPOINT_NOT_FOUND"; message = "Запрошенный URL не существует"; }
            case 405 -> { code = "METHOD_NOT_ALLOWED"; message = "HTTP-метод не поддерживается для этого URL"; }
            case 415 -> { code = "UNSUPPORTED_MEDIA_TYPE"; message = "Ожидается тело запроса в формате application/json"; }
            default -> { code = "HTTP_ERROR"; message = "Не удалось обработать HTTP-запрос"; details = Map.of("status", Integer.toString(status)); }
        }
        ApiErrorResponse body = new ApiErrorResponse(status, code, message, path(), details);
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
    }

    private String path() { return uriInfo == null ? "" : "/" + uriInfo.getPath(); }
}
