package ru.itmo.soa.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.stream.StreamSupport;

@Provider
public class ConstraintViolationExceptionMapper extends ErrorResponseMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        boolean invalidResponse = exception.getConstraintViolations().stream()
                .anyMatch(violation -> StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
                        .anyMatch(node -> node.getKind() == ElementKind.RETURN_VALUE));
        if (invalidResponse) {
            return internalError(exception);
        }
        return respond(ApiException.validation(RequestValidation.violations(exception.getConstraintViolations())));
    }
}
