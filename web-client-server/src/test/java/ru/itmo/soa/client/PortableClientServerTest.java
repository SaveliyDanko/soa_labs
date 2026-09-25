package ru.itmo.soa.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class PortableClientServerTest {
    @Test
    void errorsIncludeThePathAndEscapeJson() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                PortableClientServer.error(exchange, 503, "UPSTREAM_UNAVAILABLE", "Ошибка \"сервиса\"\nПовторите \\ запрос");
            }
        });
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/study-groups?secret=value");
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(503, response.statusCode());
            assertEquals("ru", response.headers().firstValue("Content-Language").orElseThrow());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
            assertTrue(response.body().contains("\"path\":\"/api/study-groups\""));
            assertTrue(response.body().contains("\"status\":503"));
            assertTrue(response.body().contains("Ошибка \\\"сервиса\\\"\\u000aПовторите \\\\ запрос"));
            assertFalse(response.body().contains("secret"));

            HttpResponse<String> head = client.send(HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(503, head.statusCode());
            assertEquals("", head.body());
        } finally {
            server.stop(0);
        }
    }
}
