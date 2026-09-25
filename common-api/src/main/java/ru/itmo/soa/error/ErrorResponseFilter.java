package ru.itmo.soa.error;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;

/** Обрабатывает готовые HTTP-ошибки, которые JAX-RS может вернуть без ExceptionMapper. */
@Provider
public class ErrorResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (response.getStatus() < 400 || response.getEntity() instanceof ApiErrorResponse) return;
        response.setEntity(ErrorResponses.body(ApiException.http(response.getStatus()),
                request.getUriInfo().getRequestUri().getRawPath()));
        ErrorResponses.prepareHeaders(response.getHeaders());
    }
}
