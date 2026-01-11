package com.thomas.espdoorbell.doorbell.core.exception

import com.auth0.jwt.exceptions.JWTVerificationException
import com.thomas.espdoorbell.doorbell.core.exception.models.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class ExceptionHandler {
    private val logger = LoggerFactory.getLogger(ExceptionHandler::class.java)

    private fun <T : Exception> generateTemplate(
        ex: T,
        status: HttpStatus,
        path: String,
        method: String
    ): ErrorResponse<T> = ErrorResponse(
        timestamp = LocalDateTime.now(),
        code = status.value(),
        error = ex.javaClass,
        message = ex.message ?: status.reasonPhrase,
        path = path,
        method = method
    )

    @ExceptionHandler(DomainException.EntityNotFound::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleEntityNotFound(ex: DomainException.EntityNotFound, req: ServerHttpRequest): ErrorResponse<DomainException.EntityNotFound> {
        logger.warn("Entity not found: ${ex.message} [${req.method} ${req.path}]")
        return generateTemplate(ex, HttpStatus.NOT_FOUND, "${req.path}", "${req.method}")
    }

    @ExceptionHandler(DomainException.EntityConflict::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleEntityConflict(ex: DomainException.EntityConflict, req: ServerHttpRequest): ErrorResponse<DomainException.EntityConflict> {
        logger.warn("Entity conflict: ${ex.message} [${req.method} ${req.path}]")
        return generateTemplate(ex, HttpStatus.CONFLICT, "${req.path}", "${req.method}")
    }

    @ExceptionHandler(DomainException.InvalidOperation::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidOperation(ex: DomainException.InvalidOperation, req: ServerHttpRequest): ErrorResponse<DomainException.InvalidOperation> {
        logger.warn("Invalid operation: ${ex.message} [${req.method} ${req.path}]")
        return generateTemplate(ex, HttpStatus.BAD_REQUEST, "${req.path}", "${req.method}")
    }

    @ExceptionHandler(DomainException.AccessDenied::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleDomainAccessDenied(ex: DomainException.AccessDenied, req: ServerHttpRequest): ErrorResponse<DomainException.AccessDenied> {
        logger.warn("Access denied: ${ex.message} [${req.method} ${req.path}]")
        return generateTemplate(ex, HttpStatus.FORBIDDEN, "${req.path}", "${req.method}")
    }

    @ExceptionHandler(JWTVerificationException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleJwtVerification(ex: JWTVerificationException, req: ServerHttpRequest): ErrorResponse<JWTVerificationException> {
        logger.warn("JWT verification failed: ${ex.message} [${req.method} ${req.path}]")
        return generateTemplate(ex, HttpStatus.UNAUTHORIZED, "${req.path}", "${req.method}")
    }

    @ExceptionHandler(BadCredentialsException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleBadCredentials(ex: BadCredentialsException, req: ServerHttpRequest): ErrorResponse<BadCredentialsException> {
        logger.warn("Bad credentials: ${ex.message} [${req.method} ${req.path}]")
        return generateTemplate(ex, HttpStatus.UNAUTHORIZED, "${req.path}", "${req.method}")
    }

    @ExceptionHandler(AccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleAccessDenied(ex: AccessDeniedException, req: ServerHttpRequest): ErrorResponse<AccessDeniedException> {
        logger.warn("Device access denied: ${ex.message} [${req.method} ${req.path}]")
        return generateTemplate(ex, HttpStatus.FORBIDDEN, "${req.path}", "${req.method}")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(ex: IllegalArgumentException, req: ServerHttpRequest): ErrorResponse<IllegalArgumentException> {
        logger.warn("Illegal argument: ${ex.message} [${req.method} ${req.path}]")
        return generateTemplate(ex, HttpStatus.BAD_REQUEST, "${req.path}", "${req.method}")
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGeneric(ex: Exception, req: ServerHttpRequest): ErrorResponse<Exception> {
        logger.error("Unhandled exception [${req.method} ${req.path}]", ex)
        return generateTemplate(ex, HttpStatus.INTERNAL_SERVER_ERROR, "${req.path}", "${req.method}")
    }
}
