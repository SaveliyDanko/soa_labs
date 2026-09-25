package ru.itmo.soa.error;

import jakarta.ws.rs.core.Application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Явная регистрация общих обработчиков и ресурсов на Payara и WildFly. */
public abstract class ApiApplication extends Application {
    private final Set<Class<?>> classes;

    protected ApiApplication(Class<?>... resources) {
        Set<Class<?>> registered = new HashSet<>(List.of(resources));
        registered.addAll(Set.of(ApiExceptionMapper.class, WebApplicationExceptionMapper.class,
                ConstraintViolationExceptionMapper.class, UnexpectedExceptionMapper.class, ErrorResponseFilter.class));
        classes = Set.copyOf(registered);
    }

    @Override
    public Set<Class<?>> getClasses() { return classes; }
}
