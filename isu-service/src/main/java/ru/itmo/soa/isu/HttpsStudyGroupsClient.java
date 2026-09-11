package ru.itmo.soa.isu;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.soa.model.ApiModels.StudyGroup;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;

import java.util.Map;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

@ApplicationScoped
public class HttpsStudyGroupsClient implements StudyGroupsClient {
    private final Client client;
    private final String baseUrl;

    public HttpsStudyGroupsClient() {
        this.baseUrl = configuredBaseUrl();
        if (!baseUrl.toLowerCase().startsWith("https://")) {
            throw new IllegalStateException("STUDY_GROUPS_BASE_URL must use https://");
        }
        ClientBuilder builder = ClientBuilder.newBuilder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS);
        SSLContext sslContext = configuredSslContext();
        if (sslContext != null) builder.sslContext(sslContext);
        this.client = builder.build();
    }

    @Override
    public StudyGroup get(int id) {
        Invocation invocation = client.target(baseUrl).path("api/study-groups").path(Integer.toString(id))
                .request(MediaType.APPLICATION_JSON_TYPE).buildGet();
        return execute(invocation, 200, StudyGroup.class);
    }

    @Override
    public StudyGroup update(int id, StudyGroupRequest request) {
        Invocation invocation = client.target(baseUrl).path("api/study-groups").path(Integer.toString(id))
                .request(MediaType.APPLICATION_JSON_TYPE)
                .buildPut(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE));
        return execute(invocation, 200, StudyGroup.class);
    }

    @Override
    public void delete(int id) {
        Invocation invocation = client.target(baseUrl).path("api/study-groups").path(Integer.toString(id))
                .request(MediaType.APPLICATION_JSON_TYPE).buildDelete();
        execute(invocation, 204, null);
    }

    private <T> T execute(Invocation invocation, int expectedStatus, Class<T> responseType) {
        final Response response;
        try {
            response = invocation.invoke();
        } catch (ProcessingException exception) {
            throw new ApiException(503, "STUDY_GROUPS_SERVICE_UNAVAILABLE",
                    "Сервис Study Groups временно недоступен", Map.of(), exception);
        }
        try (response) {
            int status = response.getStatus();
            if (status == 404) {
                throw new ApiException(404, "STUDY_GROUP_NOT_FOUND",
                        "Сервис Study Groups не нашёл указанную группу");
            }
            if (status != expectedStatus) {
                throw new ApiException(502, "UPSTREAM_BAD_RESPONSE",
                        "Сервис Study Groups вернул ошибочный ответ",
                        Map.of("upstreamStatus", Integer.toString(status)));
            }
            if (responseType == null) return null;
            try {
                return response.readEntity(responseType);
            } catch (ProcessingException | IllegalStateException exception) {
                throw new ApiException(502, "UPSTREAM_BAD_RESPONSE",
                        "Не удалось обработать ответ сервиса Study Groups", Map.of(), exception);
            }
        }
    }

    @PreDestroy
    public void close() {
        client.close();
    }

    private static String configuredBaseUrl() {
        String configured = System.getProperty("study.groups.base.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("STUDY_GROUPS_BASE_URL");
        if (configured == null || configured.isBlank()) configured = "https://localhost:8181";
        return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }

    private static SSLContext configuredSslContext() {
        String trustStorePath = System.getProperty("study.groups.trust-store");
        if (trustStorePath == null || trustStorePath.isBlank()) trustStorePath = System.getenv("STUDY_GROUPS_TRUSTSTORE");
        if (trustStorePath == null || trustStorePath.isBlank()) return null;
        String password = System.getProperty("study.groups.trust-store-password");
        if (password == null) password = System.getenv("STUDY_GROUPS_TRUSTSTORE_PASSWORD");
        if (password == null) password = "changeit";
        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(Path.of(trustStorePath))) {
                trustStore.load(input, password.toCharArray());
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Cannot load Study Groups TLS trust store", exception);
        }
    }
}
