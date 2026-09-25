package ru.itmo.soa.client;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dependency-free Java 17 HTTPS host for the browser client. It also provides a
 * same-origin reverse proxy for the two application servers and Swagger UI.
 */
public final class PortableClientServer {
    private static final Logger LOGGER = Logger.getLogger(PortableClientServer.class.getName());
    private static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    private static final Set<String> FORWARDED_REQUEST_HEADERS = Set.of("accept", "content-type", "origin");
    private static final Map<String, String> STATIC_MEDIA_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    private PortableClientServer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("check")) {
            check(args);
            return;
        }
        if (args.length > 0 && args[0].equals("configure-payara")) {
            configurePayara(args);
            return;
        }

        Config config = Config.fromEnvironment();
        SSLContext serverTls = keyStoreContext(config.keyStore(), config.storePassword(), true);
        SSLContext upstreamTls = keyStoreContext(config.trustStore(), config.storePassword(), false);
        HttpClient upstreamClient = HttpClient.newBuilder()
                .sslContext(upstreamTls)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpsServer server = HttpsServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverTls) {
            @Override
            public void configure(com.sun.net.httpserver.HttpsParameters parameters) {
                SSLParameters sslParameters = getSSLContext().getDefaultSSLParameters();
                sslParameters.setNeedClientAuth(false);
                parameters.setSSLParameters(sslParameters);
            }
        });
        ExecutorService executor = Executors.newFixedThreadPool(config.workerThreads());
        server.setExecutor(executor);

        ProxyHandler studyGroups = new ProxyHandler(upstreamClient, config.studyGroupsBaseUrl());
        ProxyHandler isu = new ProxyHandler(upstreamClient, config.isuBaseUrl());
        server.createContext("/api/", studyGroups);
        server.createContext("/isu/", isu);
        server.createContext("/swagger-ui.html", studyGroups);
        server.createContext("/openapi.yaml", studyGroups);
        server.createContext("/webjars/", studyGroups);
        server.createContext("/health", exchange -> json(exchange, 200,
                "{\"status\":\"UP\",\"service\":\"web-client\"}"));
        server.createContext("/", new StaticHandler());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(2);
            executor.shutdown();
        }, "web-client-shutdown"));
        server.start();
        System.out.printf("Веб-клиент доступен по адресу https://%s:%d%n", config.bindAddress(), config.port());
    }

    private static final class ProxyHandler implements HttpHandler {
        private final HttpClient client;
        private final URI upstream;

        private ProxyHandler(HttpClient client, URI upstream) {
            this.client = client;
            this.upstream = upstream;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                byte[] requestBody = limitedBody(exchange.getRequestBody());
                String rawPath = exchange.getRequestURI().getRawPath();
                String rawQuery = exchange.getRequestURI().getRawQuery();
                URI target = URI.create(upstream.toString() + rawPath + (rawQuery == null ? "" : "?" + rawQuery));
                HttpRequest.Builder builder = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(20));
                exchange.getRequestHeaders().forEach((name, values) -> {
                    if (FORWARDED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                        values.forEach(value -> builder.header(name, value));
                    }
                });
                HttpRequest.BodyPublisher publisher = requestBody.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(requestBody);
                builder.method(exchange.getRequestMethod(), publisher);

                HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                for (String header : List.of("Allow", "WWW-Authenticate", "Retry-After", "Content-Language")) {
                    response.headers().firstValue(header)
                            .ifPresent(value -> exchange.getResponseHeaders().set(header, value));
                }
                response.headers().firstValue("Content-Type")
                        .ifPresent(value -> exchange.getResponseHeaders().set("Content-Type", value));
                response.headers().firstValue("Location")
                        .map(value -> rewriteLocation(exchange, value))
                        .ifPresent(value -> exchange.getResponseHeaders().set("Location", value));
                byte[] body = response.body();
                if (exchange.getRequestMethod().equalsIgnoreCase("HEAD") || response.statusCode() == 204) {
                    exchange.sendResponseHeaders(response.statusCode(), -1);
                } else {
                    exchange.sendResponseHeaders(response.statusCode(), body.length);
                    exchange.getResponseBody().write(body);
                }
            } catch (RequestTooLargeException exception) {
                error(exchange, 413, "REQUEST_TOO_LARGE", "Тело запроса превышает 2 МБ");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                error(exchange, 503, "UPSTREAM_UNAVAILABLE", "Вызов сервиса был прерван");
            } catch (IOException exception) {
                LOGGER.log(Level.WARNING, "Не удалось выполнить запрос к сервису", exception);
                error(exchange, 503, "UPSTREAM_UNAVAILABLE", "Сервис временно недоступен");
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Необработанная ошибка прокси", exception);
                error(exchange, 500, "INTERNAL_SERVER_ERROR", "Внутренняя ошибка сервера");
            } finally {
                exchange.close();
            }
        }

        private String rewriteLocation(HttpExchange exchange, String value) {
            try {
                URI location = URI.create(value);
                String host = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Host"))
                        .orElse("localhost:" + exchange.getLocalAddress().getPort());
                return "https://" + host + location.getRawPath()
                        + (location.getRawQuery() == null ? "" : "?" + location.getRawQuery());
            } catch (IllegalArgumentException exception) {
                return value;
            }
        }
    }

    private static final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
                    exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                    error(exchange, 405, "METHOD_NOT_ALLOWED", "Для статического ресурса доступны только методы GET и HEAD");
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/")) path = "/index.html";
                if (path.contains("..")) {
                    error(exchange, 400, "INVALID_PATH", "Некорректный путь");
                    return;
                }
                InputStream input = PortableClientServer.class.getResourceAsStream("/public" + path);
                if (input == null && !path.substring(path.lastIndexOf('/') + 1).contains(".")) {
                    input = PortableClientServer.class.getResourceAsStream("/public/index.html");
                    path = "/index.html";
                }
                if (input == null) {
                    error(exchange, 404, "RESOURCE_NOT_FOUND", "Статический ресурс не найден");
                    return;
                }
                byte[] body;
                try (InputStream resource = input) {
                    body = resource.readAllBytes();
                }
                String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                exchange.getResponseHeaders().set("Content-Type",
                        STATIC_MEDIA_TYPES.getOrDefault(extension, "application/octet-stream"));
                exchange.getResponseHeaders().set("Cache-Control",
                        path.endsWith("index.html") ? "no-cache" : "public, max-age=3600");
                if (exchange.getRequestMethod().equals("HEAD")) {
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            } finally {
                exchange.close();
            }
        }
    }

    private record Config(String bindAddress, int port, int workerThreads, Path keyStore,
                          Path trustStore, String storePassword, URI studyGroupsBaseUrl, URI isuBaseUrl) {
        private static Config fromEnvironment() {
            String password = env("TLS_STORE_PASSWORD", "changeit");
            return new Config(
                    env("CLIENT_BIND_ADDRESS", "0.0.0.0"),
                    integerEnv("CLIENT_HTTPS_PORT", 3000),
                    integerEnv("CLIENT_WORKER_THREADS", 16),
                    Path.of(env("SERVER_KEYSTORE", "config/tls/server.p12")),
                    Path.of(env("SERVER_TRUSTSTORE", "config/tls/truststore.p12")),
                    password,
                    httpsUri(env("STUDY_GROUPS_BASE_URL", "https://localhost:8181")),
                    httpsUri(env("ISU_BASE_URL", "https://localhost:8443")));
        }
    }

    private static void check(String[] args) throws Exception {
        if (args.length != 5 && args.length != 6) {
            throw new IllegalArgumentException("Использование: java -jar web-client.jar check <HTTPS-адрес> <хранилище-сертификатов> <пароль> <секунды> [код[,код...]]");
        }
        URI uri = httpsUri(args[1]);
        SSLContext tls = keyStoreContext(Path.of(args[2]), args[3], false);
        HttpClient client = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.nanoTime() + Duration.ofSeconds(Long.parseLong(args[4])).toNanos();
        Exception lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (args.length == 6 && !Set.of(args[5].split(","))
                        .contains(Integer.toString(response.statusCode()))) {
                    throw new IOException("Неожиданный код ответа HTTP: " + response.statusCode());
                }
                System.out.printf("Сервис готов: %s (HTTP %d)%n", uri, response.statusCode());
                return;
            } catch (IOException exception) {
                lastError = exception;
                Thread.sleep(500);
            }
        }
        throw new IOException("Истекло время ожидания сервиса " + uri, lastError);
    }

    private static void configurePayara(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException("Использование: java -jar web-client.jar configure-payara <domain.xml> <HTTPS-адрес> <HTTPS-порт> <отключённый-HTTP-порт> <процессоры> <размер-стека> <максимальная-куча>");
        }
        Path domainXml = Path.of(args[1]);
        String bindAddress = args[2];
        int httpsPort = validPort(args[3]);
        int httpPort = validPort(args[4]);
        int activeProcessors = positiveInteger(args[5], "количество активных процессоров");
        String threadStackSize = memorySize(args[6], "размер стека потока");
        String maxHeap = memorySize(args[7], "максимальный размер кучи");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(domainXml.toFile());
        Element serverConfig = namedElement(document.getElementsByTagName("config"), "server-config");
        if (serverConfig == null) {
            throw new IllegalStateException("Конфигурация Payara server-config не найдена в " + domainXml);
        }

        Element http = requiredNamedElement(serverConfig, "network-listener", "http-listener-1");
        http.setAttribute("enabled", "false");
        http.setAttribute("address", "127.0.0.1");
        http.setAttribute("port", Integer.toString(httpPort));

        Element https = requiredNamedElement(serverConfig, "network-listener", "http-listener-2");
        https.setAttribute("enabled", "true");
        https.setAttribute("address", bindAddress);
        https.setAttribute("port", Integer.toString(httpsPort));

        Element admin = requiredNamedElement(serverConfig, "network-listener", "admin-listener");
        admin.setAttribute("address", "127.0.0.1");

        Element httpsProtocol = requiredNamedElement(serverConfig, "protocol", "http-listener-2");
        NodeList sslNodes = httpsProtocol.getElementsByTagName("ssl");
        if (sslNodes.getLength() == 0) {
            throw new IllegalStateException("Настройки HTTPS для Payara не найдены");
        }
        ((Element) sslNodes.item(0)).setAttribute("cert-nickname", "s1as");

        configurePayaraResources(document, serverConfig, activeProcessors, threadStackSize, maxHeap);

        Path temporary = domainXml.resolveSibling(domainXml.getFileName() + ".tmp");
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(document), new StreamResult(temporary.toFile()));
        Files.move(temporary, domainXml, StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("В файле %s настроены подключения Payara только по HTTPS%n", domainXml);
    }

    private static void configurePayaraResources(Document document, Element serverConfig,
                                                  int activeProcessors, String threadStackSize,
                                                  String maxHeap) {
        setThreadPool(serverConfig, "admin-thread-pool", 1, 4);
        setThreadPool(serverConfig, "http-thread-pool", 1, 8);
        setThreadPool(serverConfig, "thread-pool-1", 1, 4);

        NodeList hazelcastNodes = serverConfig.getElementsByTagName("hazelcast-config-specific-configuration");
        if (hazelcastNodes.getLength() > 0) {
            ((Element) hazelcastNodes.item(0)).setAttribute("enabled", "false");
        }
        NodeList iiopListeners = serverConfig.getElementsByTagName("iiop-listener");
        for (int index = 0; index < iiopListeners.getLength(); index++) {
            ((Element) iiopListeners.item(index)).setAttribute("address", "127.0.0.1");
        }
        NodeList jmxConnectors = serverConfig.getElementsByTagName("jmx-connector");
        for (int index = 0; index < jmxConnectors.getLength(); index++) {
            ((Element) jmxConnectors.item(index)).setAttribute("address", "127.0.0.1");
        }

        NodeList javaConfigs = serverConfig.getElementsByTagName("java-config");
        if (javaConfigs.getLength() == 0) {
            throw new IllegalStateException("Конфигурация Payara java-config не найдена");
        }
        Element javaConfig = (Element) javaConfigs.item(0);
        NodeList options = javaConfig.getElementsByTagName("jvm-options");
        for (int index = options.getLength() - 1; index >= 0; index--) {
            Node option = options.item(index);
            String value = option.getTextContent().trim();
            if (value.startsWith("-Xms") || value.startsWith("-Xmx") || value.startsWith("-Xss")
                    || value.startsWith("-XX:ActiveProcessorCount=")
                    || value.matches("-XX:[+-]Use.+GC")) {
                javaConfig.removeChild(option);
            }
        }
        appendJvmOption(document, javaConfig, "-Xms64m");
        appendJvmOption(document, javaConfig, "-Xmx" + maxHeap);
        appendJvmOption(document, javaConfig, "-Xss" + threadStackSize);
        appendJvmOption(document, javaConfig, "-XX:ActiveProcessorCount=" + activeProcessors);
        appendJvmOption(document, javaConfig, "-XX:+UseSerialGC");
    }

    private static void setThreadPool(Element serverConfig, String name, int minimum, int maximum) {
        Element pool = requiredNamedElement(serverConfig, "thread-pool", name);
        pool.setAttribute("min-thread-pool-size", Integer.toString(minimum));
        pool.setAttribute("max-thread-pool-size", Integer.toString(maximum));
    }

    private static void appendJvmOption(Document document, Element javaConfig, String value) {
        Element option = document.createElement("jvm-options");
        option.setTextContent(value);
        javaConfig.appendChild(option);
    }

    private static Element requiredNamedElement(Element parent, String tag, String name) {
        Element element = namedElement(parent.getElementsByTagName(tag), name);
        if (element == null) {
            throw new IllegalStateException("Элемент Payara " + tag + " с именем " + name + " не найден");
        }
        return element;
    }

    private static Element namedElement(NodeList nodes, String name) {
        for (int index = 0; index < nodes.getLength(); index++) {
            Element candidate = (Element) nodes.item(index);
            if (name.equals(candidate.getAttribute("name"))) return candidate;
        }
        return null;
    }

    private static int validPort(String value) {
        int port = Integer.parseInt(value);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Некорректный TCP-порт: " + value);
        return port;
    }

    private static int positiveInteger(String value, String label) {
        int number = Integer.parseInt(value);
        if (number < 1) throw new IllegalArgumentException("Некорректное значение параметра «" + label + "»: " + value);
        return number;
    }

    private static String memorySize(String value, String label) {
        if (!value.matches("[1-9][0-9]*[kKmMgG]")) {
            throw new IllegalArgumentException("Некорректное значение параметра «" + label + "»: " + value);
        }
        return value;
    }

    private static SSLContext keyStoreContext(Path path, String password, boolean withPrivateKey)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(path)) {
            store.load(input, password.toCharArray());
        }
        SSLContext context = SSLContext.getInstance("TLS");
        if (withPrivateKey) {
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, password.toCharArray());
            context.init(keys.getKeyManagers(), null, null);
        } else {
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            context.init(null, trust.getTrustManagers(), null);
        }
        return context;
    }

    private static byte[] limitedBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_REQUEST_BYTES) throw new RequestTooLargeException();
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static URI httpsUri(String value) {
        URI uri = URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Адрес сервиса должен использовать протокол https://: " + value);
        }
        return uri;
    }

    private static int integerEnv(String name, int defaultValue) {
        return Integer.parseInt(env(name, Integer.toString(defaultValue)));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static void error(HttpExchange exchange, int status, String code, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Language", "ru");
        json(exchange, status, "{\"timestamp\":" + jsonString(Instant.now().toString())
                + ",\"status\":" + status + ",\"code\":" + jsonString(code)
                + ",\"message\":" + jsonString(message)
                + ",\"path\":" + jsonString(exchange.getRequestURI().getRawPath()) + "}");
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        if (exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static final class RequestTooLargeException extends IOException {
    }
}
