package ru.itmo.soa.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class WebApplicationExceptionMapper extends ErrorResponseMapper<WebApplicationException> {
    @Override
    public Response toResponse(WebApplicationException exception) {
        Response original = exception.getResponse();
        // Сохраняем служебные заголовки (Allow, WWW-Authenticate, Retry-After), заменяя тело ответа.
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>(original.getHeaders());
        ErrorResponses.prepareHeaders(headers);
        Response.ResponseBuilder response = Response.fromResponse(original).replaceAll(headers);
        return respond(ApiException.http(original.getStatus()), response);
    }
}
