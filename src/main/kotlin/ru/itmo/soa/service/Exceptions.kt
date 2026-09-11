package ru.itmo.soa.service

import org.springframework.http.HttpStatus

class NotFoundException(
    val code: String,
    message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)

class InvalidQueryException(
    val code: String,
    message: String,
    val details: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class UpstreamServiceException(
    val status: HttpStatus,
    val code: String,
    message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)
