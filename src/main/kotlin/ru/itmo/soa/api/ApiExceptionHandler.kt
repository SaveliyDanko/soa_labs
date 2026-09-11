package ru.itmo.soa.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import ru.itmo.soa.api.dto.ApiErrorResponse
import ru.itmo.soa.api.dto.ValidationErrorResponse
import ru.itmo.soa.service.InvalidQueryException
import ru.itmo.soa.service.NotFoundException
import ru.itmo.soa.service.UpstreamServiceException
import java.time.Instant

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException::class)
    fun notFound(exception: NotFoundException, request: HttpServletRequest) = apiError(
        HttpStatus.NOT_FOUND,
        exception.code,
        exception.message ?: "Ресурс не найден",
        request,
        exception.details,
    )

    @ExceptionHandler(InvalidQueryException::class)
    fun invalidQuery(exception: InvalidQueryException, request: HttpServletRequest) = apiError(
        HttpStatus.BAD_REQUEST,
        exception.code,
        exception.message ?: "Некорректные параметры запроса",
        request,
        exception.details,
    )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformedJson(exception: HttpMessageNotReadableException, request: HttpServletRequest) = apiError(
        HttpStatus.BAD_REQUEST,
        "MALFORMED_JSON",
        "Тело запроса содержит некорректный JSON или недопустимое значение enum",
        request,
    )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(exception: MethodArgumentTypeMismatchException, request: HttpServletRequest) = apiError(
        HttpStatus.BAD_REQUEST,
        "INVALID_PARAMETER_TYPE",
        "Параметр '${exception.name}' имеет неверный тип",
        request,
        mapOf(
            "parameter" to exception.name,
            "value" to (exception.value?.toString() ?: "null"),
            "expectedType" to (exception.requiredType?.simpleName ?: "unknown"),
        ),
    )

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(exception: MissingServletRequestParameterException, request: HttpServletRequest) = apiError(
        HttpStatus.BAD_REQUEST,
        "MISSING_PARAMETER",
        "Отсутствует обязательный параметр '${exception.parameterName}'",
        request,
        mapOf("parameter" to exception.parameterName),
    )

    @ExceptionHandler(ConstraintViolationException::class)
    fun invalidParameter(exception: ConstraintViolationException, request: HttpServletRequest) = validationError(
        HttpStatus.BAD_REQUEST,
        "INVALID_PARAMETER_VALUE",
        "Параметры запроса нарушают ограничения",
        request,
        exception.constraintViolations.associate {
            it.propertyPath.toString().substringAfterLast('.') to it.message
        },
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(exception: MethodArgumentNotValidException, request: HttpServletRequest) = validationError(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_FAILED",
        "JSON корректен синтаксически, но поля нарушают ограничения модели",
        request,
        exception.bindingResult.fieldErrors.associate { fieldError: FieldError ->
            fieldError.field to (fieldError.defaultMessage ?: "Недопустимое значение")
        },
    )

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun unsupportedMediaType(exception: HttpMediaTypeNotSupportedException, request: HttpServletRequest) = apiError(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "Ожидается тело запроса в формате application/json",
        request,
        mapOf("received" to (exception.contentType?.toString() ?: "not specified")),
    )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun conflict(exception: DataIntegrityViolationException, request: HttpServletRequest) = apiError(
        HttpStatus.CONFLICT,
        "DATA_INTEGRITY_VIOLATION",
        "Операция конфликтует с текущим состоянием хранилища",
        request,
    )

    @ExceptionHandler(UpstreamServiceException::class)
    fun upstream(exception: UpstreamServiceException, request: HttpServletRequest) = apiError(
        exception.status,
        exception.code,
        exception.message ?: "Ошибка сервиса Study Groups",
        request,
        exception.details,
    )

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(exception: HttpRequestMethodNotSupportedException, request: HttpServletRequest) = apiError(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "Метод ${exception.method} не поддерживается для этого URL",
        request,
        mapOf("supportedMethods" to exception.supportedHttpMethods.orEmpty().joinToString(",")),
    )

    @ExceptionHandler(NoResourceFoundException::class)
    fun endpointNotFound(exception: NoResourceFoundException, request: HttpServletRequest) = apiError(
        HttpStatus.NOT_FOUND,
        "ENDPOINT_NOT_FOUND",
        "Запрошенный URL не существует",
        request,
    )

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        logger.error("Unhandled error while processing ${request.method} ${request.requestURI}", exception)
        return apiError(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "Внутренняя ошибка сервера",
            request,
        )
    }

    private fun apiError(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
        details: Map<String, String> = emptyMap(),
    ): ResponseEntity<ApiErrorResponse> = ResponseEntity.status(status).body(
        ApiErrorResponse(
            timestamp = Instant.now(),
            status = status.value(),
            code = code,
            message = message,
            path = request.requestURI,
            details = details.ifEmpty { null },
        ),
    )

    private fun validationError(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
        violations: Map<String, String>,
    ): ResponseEntity<ValidationErrorResponse> = ResponseEntity.status(status).body(
        ValidationErrorResponse(
            timestamp = Instant.now(),
            status = status.value(),
            code = code,
            message = message,
            path = request.requestURI,
            violations = violations,
        ),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
