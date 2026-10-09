package ru.itmo.soa.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.zuul.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class GatewayErrorTest {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final GatewayErrorResponse errors = new GatewayErrorResponse(json);
    private RequestContext context;

    @BeforeEach void context() {
        context = new RequestContext();
        context.setRequest(new MockHttpServletRequest("POST", "/isu/group/1/expel-all"));
        context.setResponse(new MockHttpServletResponse());
        RequestContext.testSetCurrentContext(context);
    }
    @AfterEach void cleanup() { RequestContext.getCurrentContext().unset(); }

    @Test void connectionErrorsUseRussianJson() throws Exception {
        context.setThrowable(new IllegalStateException("connection failed"));
        GatewayErrorFilter filter = new GatewayErrorFilter(errors);
        assertTrue(filter.shouldFilter());
        filter.run();
        assertNull(context.getThrowable());
        assertEquals(503, context.getResponseStatusCode());
        var body = json.readTree(context.getResponseBody());
        assertEquals("UPSTREAM_UNAVAILABLE", body.get("code").asText());
        assertEquals("Сервис временно недоступен", body.get("message").asText());
        assertEquals("/isu/group/1/expel-all", body.get("path").asText());
    }

    @Test void haproxyHtmlErrorsUseTheSameContract() throws Exception {
        context.setResponseStatusCode(503);
        context.addZuulResponseHeader("Content-Type", "text/html");
        context.addZuulResponseHeader("Content-Length", "99");
        context.setResponseDataStream(new ByteArrayInputStream("<html>unavailable</html>".getBytes()));
        new GatewayHeadersFilter(errors).run();
        assertNull(context.getResponseDataStream());
        assertEquals("UPSTREAM_UNAVAILABLE", json.readTree(context.getResponseBody()).get("code").asText());
        assertFalse(context.getZuulResponseHeaders().stream().anyMatch(h -> h.first().equals("Content-Length")));
    }

    @Test void preservesErrorsFromApplicationServices() {
        context.setResponseStatusCode(500);
        context.addZuulResponseHeader("Content-Type", "application/json");
        context.setResponseBody("{\"code\":\"INTERNAL_SERVER_ERROR\"}");
        new GatewayHeadersFilter(errors).run();
        assertEquals("{\"code\":\"INTERNAL_SERVER_ERROR\"}", context.getResponseBody());
    }
}
