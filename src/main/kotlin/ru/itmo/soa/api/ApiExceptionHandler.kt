package ru.itmo.soa.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import ru.itmo.soa.api.dto.ErrorResponse
import ru.itmo.soa.service.InvalidQueryException
import ru.itmo.soa.service.NotFoundException
import ru.itmo.soa.service.UpstreamServiceException
import java.time.Instant

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException::class)
    fun notFound(exception: NotFoundException, request: HttpServletRequest) =
        error(HttpStatus.NOT_FOUND, exception.message ?: "Ресурс не найден", request)

    @ExceptionHandler(
        InvalidQueryException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
        ConstraintViolationException::class,
    )
    fun badRequest(exception: Exception, request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST, readableMessage(exception), request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val violations = linkedMapOf<String, String>()
        exception.bindingResult.fieldErrors.forEach { fieldError: FieldError ->
            violations.putIfAbsent(fieldError.field, fieldError.defaultMessage ?: "Недопустимое значение")
        }
        return error(
            HttpStatus.BAD_REQUEST,
            "Переданные данные нарушают ограничения целостности",
            request,
            violations,
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun conflict(exception: DataIntegrityViolationException, request: HttpServletRequest) =
        error(HttpStatus.CONFLICT, "Операция нарушает ограничения целостности данных", request)

    @ExceptionHandler(UpstreamServiceException::class)
    fun upstream(exception: UpstreamServiceException, request: HttpServletRequest) =
        error(exception.status, exception.message ?: "Ошибка первого сервиса", request)

    private fun error(
        status: HttpStatus,
        message: String,
        request: HttpServletRequest,
        violations: Map<String, String> = emptyMap(),
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(status).body(
        ErrorResponse(
            timestamp = Instant.now(),
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = request.requestURI,
            violations = violations,
        ),
    )

    private fun readableMessage(exception: Exception): String =
        if (exception is HttpMessageNotReadableException) {
            "Тело запроса содержит некорректный JSON или недопустимое значение enum"
        } else {
            exception.message ?: "Некорректный запрос"
        }
}
