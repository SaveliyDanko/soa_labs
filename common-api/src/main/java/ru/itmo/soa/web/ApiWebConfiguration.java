package ru.itmo.soa.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.server.WebFilter;
import org.springframework.core.annotation.Order;
import ru.itmo.soa.error.ApiExceptionHandler;
import ru.itmo.soa.error.ApiErrorReporter;
import java.util.List;

@Configuration
@Import({ApiExceptionHandler.class, ApiErrorReporter.class})
public class ApiWebConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer apiJson() {
        return builder -> builder.serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                        MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public CorsWebFilter apiCors() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("*"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        cors.setAllowedHeaders(List.of("Content-Type", "Accept", "sentry-trace", "baggage"));
        cors.setExposedHeaders(List.of("Location", "X-Service-Instance", "X-Gateway", "X-Config-Source"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }

    @Bean
    public WebFilter instanceHeaders(Environment environment) {
        return (exchange, chain) -> {
            var headers = exchange.getResponse().getHeaders();
            if ("api-gateway".equals(environment.getProperty("spring.application.name"))) {
                headers.set("X-Gateway", "spring-cloud-gateway");
            } else {
                headers.set("X-Service-Instance", environment.getProperty("lab.instance-id",
                        environment.getProperty("spring.application.name", "service") + ":"
                                + environment.getProperty("server.port", "8080")));
                headers.set("X-Config-Source", environment.getProperty("lab.config-source", "local"));
            }
            return chain.filter(exchange);
        };
    }
}
