package com.thomas.espdoorbell.doorbell.core.exception

import com.auth0.jwt.exceptions.JWTVerificationException
import org.springframework.boot.autoconfigure.web.WebProperties
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler
import org.springframework.boot.web.reactive.error.ErrorAttributes
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.RequestPredicates
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
@Order(-2)
class WebExceptionHandler(
    errAttr: ErrorAttributes,
    webProps: WebProperties,
    appCtx: ApplicationContext,
    serverCodecConfigurer: ServerCodecConfigurer
): AbstractErrorWebExceptionHandler(
    errAttr,
    webProps.resources,
    appCtx
) {
    init {
        setMessageWriters(serverCodecConfigurer.writers)
        setMessageReaders(serverCodecConfigurer.readers)
    }

    override fun getRoutingFunction(errorAttributes: ErrorAttributes?): RouterFunction<ServerResponse>
        = RouterFunctions.route(RequestPredicates.all(), ::renderErrorResponse)

    private fun renderErrorResponse(req: ServerRequest): Mono<ServerResponse> {
        val err = getError(req)

        val status = when (err) {
            // Rate limit exception (needs special header handling)
            is RateLimitExceededException -> HttpStatus.TOO_MANY_REQUESTS
            // Domain exceptions (semantic - prioritized)
            is EntityNotFoundException -> HttpStatus.NOT_FOUND
            is EntityConflictException -> HttpStatus.CONFLICT
            is InvalidOperationException -> HttpStatus.BAD_REQUEST
            // Security exceptions
            is JWTVerificationException -> HttpStatus.UNAUTHORIZED
            is BadCredentialsException -> HttpStatus.UNAUTHORIZED
            is AccessDeniedException -> HttpStatus.FORBIDDEN
            // Entity validation (from require() in entities)
            is IllegalArgumentException -> HttpStatus.BAD_REQUEST
            // Fallback
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }

        val resBody = mapOf(
            "status" to status.value(),
            "error" to status.reasonPhrase,
            "message" to (err.message ?: "Unexpected error"),
            "path" to req.path()
        )

        val responseBuilder = ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_JSON)

        // Add Retry-After header for rate limit exceptions
        if (err is RateLimitExceededException) {
            responseBuilder.header("Retry-After", err.retryAfterSeconds.toString())
        }

        return responseBuilder.body(BodyInserters.fromValue(resBody))
    }
}