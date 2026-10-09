package ru.itmo.soa.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import ru.itmo.soa.error.ApiExceptionHandler;
import ru.itmo.soa.error.ApiErrorController;
import javax.servlet.Filter;
import java.util.List;

@Configuration
@Import({ApiExceptionHandler.class, ApiErrorController.class})
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
    public FilterRegistrationBean<CorsFilter> apiCors() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("*"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        cors.setAllowedHeaders(List.of("Content-Type", "Accept"));
        cors.setExposedHeaders(List.of("Location", "X-Service-Instance", "X-Gateway", "X-Config-Source"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        FilterRegistrationBean<CorsFilter> filter = new FilterRegistrationBean<>(new CorsFilter(source));
        filter.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filter;
    }

    @Bean
    public FilterRegistrationBean<Filter> instanceHeaders(Environment environment) {
        FilterRegistrationBean<Filter> filter = new FilterRegistrationBean<>();
        filter.setFilter((request, response, chain) -> {
            javax.servlet.http.HttpServletResponse http = (javax.servlet.http.HttpServletResponse) response;
            if (!"api-gateway".equals(environment.getProperty("spring.application.name"))) {
                http.setHeader("X-Service-Instance", environment.getProperty("lab.instance-id",
                    environment.getProperty("spring.application.name", "service") + ":" + environment.getProperty("server.port", "8080")));
                http.setHeader("X-Config-Source", environment.getProperty("lab.config-source", "local"));
            }
            chain.doFilter(request, response);
        });
        return filter;
    }
}
