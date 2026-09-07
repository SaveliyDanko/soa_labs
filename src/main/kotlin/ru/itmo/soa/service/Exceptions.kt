package ru.itmo.soa.service

import org.springframework.http.HttpStatus

class NotFoundException(message: String) : RuntimeException(message)

class InvalidQueryException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

class UpstreamServiceException(
    val status: HttpStatus,
    message: String,
) : RuntimeException(message)
