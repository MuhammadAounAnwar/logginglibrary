package com.ono.logginglibrary.exception

import com.fasterxml.jackson.databind.ObjectMapper
import com.ono.logginglibrary.autoconfigure.exception.DefaultGlobalExceptionHandler
import com.ono.logginglibrary.core.exception.ErrorResponse
import com.ono.logginglibrary.core.exception.OnoBusinessException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException
import reactor.core.publisher.Mono

class DefaultGlobalExceptionHandlerTest {

    // Test-local exception subclasses — in real apps these live in the app, not the library
    private class UserNotFoundException(userId: String) : OnoBusinessException(
        errorCode = "USER_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "User not found: $userId"
    )

    private class UnauthorizedException : OnoBusinessException(
        errorCode = "INVALID_CREDENTIALS",
        httpStatus = HttpStatus.UNAUTHORIZED,
        message = "Invalid email or password"
    )

    private class BadRequestException(detail: String) : OnoBusinessException(
        errorCode = "BAD_REQUEST",
        httpStatus = HttpStatus.BAD_REQUEST,
        message = detail
    )

    private val webTestClient = WebTestClient
        .bindToController(TestController())
        .controllerAdvice(DefaultGlobalExceptionHandler())
        .build()
        .mutate()
        .codecs { codecs ->
            val mapper = Jackson2ObjectMapperBuilder.json().build<ObjectMapper>()
            codecs.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(mapper))
        }
        .build()

    @Test
    fun `UserNotFoundException returns 404 Not Found`() {
        webTestClient.get().uri("/test/users/42")
            .exchange()
            .expectStatus().isNotFound
            .expectBody(ErrorResponse::class.java)
            .value { body ->
                assert(body.status == HttpStatus.NOT_FOUND.value())
                assert(body.message == "User not found: 42")
            }
    }

    @Test
    fun `UnauthorizedException returns 401 Unauthorized`() {
        webTestClient.get().uri("/test/login")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody(ErrorResponse::class.java)
            .value { body ->
                assert(body.status == HttpStatus.UNAUTHORIZED.value())
            }
    }

    @Test
    fun `BadRequestException returns 400 Bad Request`() {
        webTestClient.get().uri("/test/password")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(ErrorResponse::class.java)
            .value { body ->
                assert(body.status == HttpStatus.BAD_REQUEST.value())
            }
    }

    @Test
    fun `unhandled exception returns 500 Internal Server Error`() {
        webTestClient.get().uri("/test/error")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            .expectBody(ErrorResponse::class.java)
            .value { body ->
                assert(body.status == HttpStatus.INTERNAL_SERVER_ERROR.value())
                assert(body.message == "Internal server error")
            }
    }

    @Test
    fun `error response contains path and timestamp`() {
        webTestClient.get().uri("/test/login")
            .exchange()
            .expectBody(ErrorResponse::class.java)
            .value { body ->
                assert(body.path == "/test/login")
                assert(body.timestamp != null)
            }
    }

    @RestController
    inner class TestController {

        @GetMapping("/test/users/{id}")
        fun getUser(@PathVariable("id") id: String): Mono<String> =
            Mono.error(UserNotFoundException(id))

        @GetMapping("/test/login")
        fun login(): Mono<String> =
            Mono.error(UnauthorizedException())

        @GetMapping("/test/password")
        fun updatePassword(): Mono<String> =
            Mono.error(BadRequestException("Failed to update password for user: user@example.com"))

        @GetMapping("/test/error")
        fun error(): Mono<String> =
            Mono.error(RuntimeException("unexpected failure"))

        @GetMapping("/test/bad-input")
        fun badInput(): Mono<String> =
            Mono.error(ServerWebInputException("type mismatch on field 'type'"))

        @GetMapping("/test/gone")
        fun gone(): Mono<String> =
            Mono.error(ResponseStatusException(HttpStatus.GONE, "resource expired"))

        @GetMapping("/test/method-not-allowed")
        fun methodNotAllowed(): Mono<String> =
            Mono.error(ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "POST not allowed here"))
    }

    @Test
    fun `ServerWebInputException returns 400 with embedded status`() {
        webTestClient.get().uri("/test/bad-input")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(ErrorResponse::class.java)
            .value { body ->
                assert(body.status == 400) { "Expected 400 but got ${body.status}" }
            }
    }

    @Test
    fun `ResponseStatusException with 410 Gone returns 410`() {
        webTestClient.get().uri("/test/gone")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.GONE)
            .expectBody(ErrorResponse::class.java)
            .value { body ->
                assert(body.status == 410) { "Expected 410 but got ${body.status}" }
                assert(body.message?.contains("resource expired") == true)
            }
    }

    @Test
    fun `ResponseStatusException with 405 returns 405 not 500`() {
        webTestClient.get().uri("/test/method-not-allowed")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
            .expectBody(ErrorResponse::class.java)
            .value { body ->
                assert(body.status == 405) { "Expected 405 but got ${body.status}" }
            }
    }
}
