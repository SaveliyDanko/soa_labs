package ru.itmo.soa.studygroups;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/** Serves the lab specification and bundled Swagger UI through the root JAX-RS mapping. */
@Path("/")
public class DocumentationResource {
    private static final String SWAGGER_VERSION = "5.18.2";
    private static final Set<String> ASSETS = Set.of(
            "swagger-ui.css", "swagger-ui-bundle.js", "swagger-ui-standalone-preset.js");
    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "css", "text/css", "js", "text/javascript");

    @Context
    ServletContext servletContext;

    @GET
    @Path("openapi.yaml")
    @Produces("application/yaml")
    public Response openApi() {
        return webResource("/openapi.yaml", "application/yaml");
    }

    @GET
    @Path("swagger-ui.html")
    @Produces(MediaType.TEXT_HTML)
    public Response swaggerUi() {
        return webResource("/swagger-ui.html", MediaType.TEXT_HTML);
    }

    @GET
    @Path("webjars/swagger-ui/" + SWAGGER_VERSION + "/{file}")
    public Response swaggerAsset(@PathParam("file") String file) {
        if (!ASSETS.contains(file)) {
            throw new ApiException(404, "ENDPOINT_NOT_FOUND", "Запрошенный URL не существует");
        }
        InputStream input = getClass().getResourceAsStream(
                "/META-INF/resources/webjars/swagger-ui/" + SWAGGER_VERSION + "/" + file);
        if (input == null) {
            throw new ApiException(404, "ENDPOINT_NOT_FOUND", "Запрошенный URL не существует");
        }
        String extension = file.substring(file.lastIndexOf('.') + 1);
        return Response.ok(input, MEDIA_TYPES.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM)).build();
    }

    private Response webResource(String path, String mediaType) {
        InputStream input = servletContext.getResourceAsStream(path);
        if (input == null) {
            throw new ApiException(404, "ENDPOINT_NOT_FOUND", "Запрошенный URL не существует");
        }
        return Response.ok(input, mediaType).build();
    }
}
