package ru.itmo.soa.isu;

import java.util.Map;
import javax.xml.ws.Endpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.env.Environment;
import javax.servlet.Filter;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class IsuSoapConfiguration {
    @Bean
    public FilterRegistrationBean<Filter> soapInstanceHeader(Environment environment) {
        FilterRegistrationBean<Filter> filter = new FilterRegistrationBean<>();
        filter.setFilter((request, response, chain) -> {
            ((HttpServletResponse) response).setHeader("X-SOAP-Instance", environment.getProperty("lab.instance-id", "isu-service"));
            chain.doFilter(request, response);
        });
        filter.addUrlPatterns("/soap/*");
        return filter;
    }

    @Bean
    public Endpoint isuEndpoint(Bus bus, IsuSoapEndpoint service, Environment environment) {
        EndpointImpl endpoint = new EndpointImpl(bus, service);
        endpoint.setWsdlLocation("classpath:wsdl/isu.wsdl");
        endpoint.setPublishedEndpointUrl(environment.getProperty("SOAP_PUBLISHED_URL", "http://mule-esb:8081/soap/isu"));
        endpoint.setProperties(Map.of("schema-validation-enabled", "true", "exceptionMessageCauseEnabled", "false"));
        endpoint.publish("/isu");
        return endpoint;
    }
}
