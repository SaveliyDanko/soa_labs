package ru.itmo.soa.error;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.itmo.soa.model.ApiModels.*;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.Locale;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ErrorHandlingTest {
    @Test
    void validationIsRussianWithEnglishLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            StudyGroupRequest request = new StudyGroupRequest();
            request.setName(" ");
            request.setStudentsCount(0L);
            ApiException error = assertThrows(ApiException.class, () -> RequestValidation.validate(request, factory.getValidator()));
            assertEquals(422, error.getStatus());
            assertEquals(ValidationMessages.NOT_BLANK, error.getViolations().get("name"));
            assertEquals(ValidationMessages.POSITIVE, error.getViolations().get("studentsCount"));
            assertEquals(ValidationMessages.REQUIRED, error.getViolations().get("coordinates"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void responsePreservesTheLab2Contract() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/study-groups/7");
        var response = new ApiExceptionHandler().api(new ApiException(ErrorCode.STUDY_GROUP_NOT_FOUND, Map.of("id", "7")), request);
        ApiErrorResponse body = (ApiErrorResponse) response.getBody();
        assertEquals(404, response.getStatusCodeValue());
        assertEquals("ru", response.getHeaders().getContentLanguage().getLanguage());
        assertNotNull(body.getTimestamp());
        assertEquals("/api/study-groups/7", body.getPath());
        assertEquals("STUDY_GROUP_NOT_FOUND", body.getCode());
        assertEquals(Map.of("id", "7"), body.getDetails());
    }

    @ParameterizedTest
    @CsvSource({"abc, INVALID_PARAMETER_TYPE", "2147483648, INVALID_PARAMETER_TYPE",
            "0, INVALID_PARAMETER_VALUE", "-1, INVALID_PARAMETER_VALUE"})
    void validatesIds(String value, String code) {
        assertEquals(code, assertThrows(ApiException.class, () -> RequestValidation.positiveId(value, "id")).getCode());
    }

    @Test
    void supportsDefaultsAndRequiredText() {
        assertEquals(20, RequestValidation.integer(null, "size", 20, 1, 100));
        assertEquals("MISSING_PARAMETER", assertThrows(ApiException.class, () -> RequestValidation.requiredText(null, "name")).getCode());
        assertEquals("INVALID_PARAMETER_VALUE", assertThrows(ApiException.class, () -> RequestValidation.requiredText(" ", "name")).getCode());
    }
}
