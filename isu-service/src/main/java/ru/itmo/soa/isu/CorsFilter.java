package ru.itmo.soa.isu;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final String ORIGIN_PROPERTY = CorsFilter.class.getName() + ".origin";

    @Override
    public void filter(ContainerRequestContext request) {
        String origin = request.getHeaderString("Origin");
        if (origin != null) request.setProperty(ORIGIN_PROPERTY, origin);
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) request.abortWith(Response.noContent().build());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        Object origin = request.getProperty(ORIGIN_PROPERTY);
        if (origin == null) return;
        response.getHeaders().putSingle("Access-Control-Allow-Origin", origin.toString());
        response.getHeaders().putSingle("Vary", "Origin");
        response.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
        response.getHeaders().putSingle("Access-Control-Allow-Methods", "POST,OPTIONS");
        response.getHeaders().putSingle("Access-Control-Allow-Headers", "Content-Type,Accept");
    }
}
