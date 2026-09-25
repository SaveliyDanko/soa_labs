package ru.itmo.soa.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.itmo.soa.model.ApiModels.ApiErrorResponse;
import ru.itmo.soa.model.ApiModels.Coordinates;
import ru.itmo.soa.model.ApiModels.Person;
import ru.itmo.soa.model.ApiModels.StudyGroupRequest;
import ru.itmo.soa.model.ApiModels.ValidationErrorResponse;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ErrorHandlingTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator(Set.of(Locale.ENGLISH), Locale.ENGLISH, false))
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() { factory.close(); }

    @Test
    void buildsTheSameEnvelopeForBothServicesWithoutExposingTheCause() {
        ApiExceptionMapper mapper = new ApiExceptionMapper();
        mapper.uriInfo = uriInfo("https://localhost/isu/group/7/expel-all?secret=value");
        Response response = mapper.toResponse(new ApiException(ErrorCode.UPSTREAM_BAD_RESPONSE,
                Map.of("upstreamStatus", "500"), new RuntimeException("Внутренние подробности")));

        ApiErrorResponse body = (ApiErrorResponse) response.getEntity();
        assertEquals(502, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("ru", response.getLanguage().getLanguage());
        assertNotNull(body.getTimestamp());
        assertEquals(502, body.getStatus());
        assertEquals("UPSTREAM_BAD_RESPONSE", body.getCode());
        assertEquals(ErrorCode.UPSTREAM_BAD_RESPONSE.message(), body.getMessage());
        assertEquals("/isu/group/7/expel-all", body.getPath());
        assertEquals(Map.of("upstreamStatus", "500"), body.getDetails());
    }

    @ParameterizedTest
    @CsvSource({"400, BAD_REQUEST", "401, UNAUTHORIZED", "403, FORBIDDEN",
            "404, ENDPOINT_NOT_FOUND", "405, METHOD_NOT_ALLOWED", "406, NOT_ACCEPTABLE",
            "409, DATA_INTEGRITY_VIOLATION", "415, UNSUPPORTED_MEDIA_TYPE",
            "429, HTTP_ERROR", "500, INTERNAL_SERVER_ERROR", "504, HTTP_ERROR"})
    void normalizesHttpErrorsAndPreservesHeaders(int status, String code) {
        Response original = Response.status(status).entity("Untranslated container error")
                .header("Allow", "GET, HEAD").header("WWW-Authenticate", "Bearer")
                .header("Retry-After", "30").header("content-length", "123")
                .header("content-encoding", "gzip").tag("old-entity").build();
        Response response = new WebApplicationExceptionMapper().toResponse(new WebApplicationException(original));

        ApiErrorResponse body = (ApiErrorResponse) response.getEntity();
        assertEquals(status, response.getStatus());
        assertEquals(status, body.getStatus());
        assertEquals(code, body.getCode());
        assertEquals(ErrorCode.forHttpStatus(status).message(), body.getMessage());
        assertEquals("GET, HEAD", response.getHeaderString("Allow"));
        assertEquals("Bearer", response.getHeaderString("WWW-Authenticate"));
        assertEquals("30", response.getHeaderString("Retry-After"));
        assertNull(response.getHeaderString("Content-Length"));
        assertNull(response.getHeaderString("Content-Encoding"));
        assertNull(response.getEntityTag());
    }

    @Test
    void validationMessagesAreRussianEvenWhenTheValidatorUsesEnglish() {
        StudyGroupRequest request = new StudyGroupRequest();
        request.setName(" ");
        request.setStudentsCount(0L);
        request.setCoordinates(new Coordinates(null, 2f));
        request.setGroupAdmin(new Person());
        ApiException error = assertThrows(ApiException.class, () -> RequestValidation.validate(request, validator));

        assertEquals(422, error.getStatus());
        assertEquals(Map.of("name", ValidationMessages.NOT_BLANK,
                "studentsCount", ValidationMessages.POSITIVE,
                "coordinates.x", ValidationMessages.REQUIRED,
                "formOfEducation", ValidationMessages.REQUIRED,
                "groupAdmin.name", ValidationMessages.NOT_BLANK,
                "groupAdmin.nationality", ValidationMessages.REQUIRED), error.getViolations());
        assertEquals(List.of("coordinates.x", "formOfEducation", "groupAdmin.name", "groupAdmin.nationality",
                "name", "studentsCount"), List.copyOf(error.getViolations().keySet()));

        Response response = new ApiExceptionMapper().toResponse(error);
        ValidationErrorResponse body = assertInstanceOf(ValidationErrorResponse.class, response.getEntity());
        assertEquals(error.getViolations(), body.getViolations());
        assertEquals(ErrorCode.VALIDATION_FAILED.message(), body.getMessage());
    }

    @Test
    void handlesMissingBodiesAndAutomaticConstraintViolations() {
        ApiException missing = assertThrows(ApiException.class, () -> RequestValidation.validate(null, validator));
        assertEquals(Map.of("request", ValidationMessages.REQUIRED), missing.getViolations());

        var violations = validator.validate(new StudyGroupRequest());
        Response response = new ConstraintViolationExceptionMapper()
                .toResponse(new ConstraintViolationException(violations));
        assertEquals(422, response.getStatus());
        ValidationErrorResponse body = assertInstanceOf(ValidationErrorResponse.class, response.getEntity());
        assertEquals(RequestValidation.violations(violations), body.getViolations());
    }

    @Test
    void returnValueValidationIsAnInternalError() throws ReflectiveOperationException {
        var violations = validator.forExecutables().validateReturnValue(
                new InvalidResource(), InvalidResource.class.getMethod("get"), null);
        ConstraintViolationExceptionMapper mapper = new ConstraintViolationExceptionMapper();
        mapper.uriInfo = uriInfo("https://localhost/api/study-groups");
        Response response = mapper.toResponse(new ConstraintViolationException(violations));

        assertEquals(500, response.getStatus());
        ApiErrorResponse body = (ApiErrorResponse) response.getEntity();
        assertEquals("INTERNAL_SERVER_ERROR", body.getCode());
        assertEquals("/api/study-groups", body.getPath());
        assertNull(body.getDetails());
        assertFalse(body instanceof ValidationErrorResponse);
    }

    @Test
    void unexpectedErrorsNeverExposeExceptionMessages() {
        Response response = new UnexpectedExceptionMapper().toResponse(new IllegalStateException("secret"));

        ApiErrorResponse body = (ApiErrorResponse) response.getEntity();
        assertEquals(500, body.getStatus());
        assertEquals("Внутренняя ошибка сервера", body.getMessage());
        assertNull(body.getDetails());
    }

    @ParameterizedTest
    @CsvSource({"abc, INVALID_PARAMETER_TYPE", "2147483648, INVALID_PARAMETER_TYPE",
            "0, INVALID_PARAMETER_VALUE", "-1, INVALID_PARAMETER_VALUE"})
    void validatesIdsConsistently(String value, String code) {
        for (String name : List.of("id", "groupId")) {
            ApiException error = assertThrows(ApiException.class, () -> RequestValidation.positiveId(value, name));
            assertEquals(400, error.getStatus());
            assertEquals(code, error.getCode());
            assertEquals(name, error.getDetails().get("parameter"));
            assertEquals(value, error.getDetails().get("value"));
        }
    }

    @Test
    void supportsDefaultsAndRejectsMissingOrBlankParameters() {
        assertEquals(20, RequestValidation.integer(null, "size", 20, 1, 100));
        assertEquals(1, RequestValidation.positiveId("1", "id"));
        assertEquals("Anna", RequestValidation.requiredText("Anna", "adminName"));
        assertEquals("MISSING_PARAMETER", assertThrows(ApiException.class,
                () -> RequestValidation.positiveId(null, "id")).getCode());
        assertEquals("MISSING_PARAMETER", assertThrows(ApiException.class,
                () -> RequestValidation.requiredText(null, "substring")).getCode());
        assertEquals("INVALID_PARAMETER_VALUE", assertThrows(ApiException.class,
                () -> RequestValidation.requiredText("  ", "substring")).getCode());
    }

    private static UriInfo uriInfo(String uri) {
        return (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(), new Class<?>[]{UriInfo.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getRequestUri")) return URI.create(uri);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    public static class InvalidResource {
        @NotNull(message = ValidationMessages.REQUIRED)
        public String get() { return null; }
    }
}
