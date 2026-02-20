package com.ono.logginglibrary.exception

import com.fasterxml.jackson.databind.ObjectMapper
import com.ono.logginglibrary.autoconfigure.exception.DefaultGlobalExceptionHandler
import com.ono.logginglibrary.core.exception.ErrorResponse
import com.ono.logginglibrary.core.exception.InvalidCredentialsException
import com.ono.logginglibrary.core.exception.NoSuchUserException
import com.ono.logginglibrary.core.exception.PasswordUpdateFailedException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

class DefaultGlobalExceptionHandlerTest {

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
    fun `NoSuchUserException returns 404 Not Found`() {
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
    fun `InvalidCredentialsException returns 401 Unauthorized`() {
        webTestClient.get().uri("/test/login")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody(ErrorResponse::class.java)
            .value { body ->
                assert(body.status == HttpStatus.UNAUTHORIZED.value())
            }
    }

    @Test
    fun `PasswordUpdateFailedException returns 400 Bad Request`() {
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
    class TestController {

        @GetMapping("/test/users/{id}")
        fun getUser(@PathVariable id: String): Mono<String> =
            Mono.error(NoSuchUserException(id))

        @GetMapping("/test/login")
        fun login(): Mono<String> =
            Mono.error(InvalidCredentialsException())

        @GetMapping("/test/password")
        fun updatePassword(): Mono<String> =
            Mono.error(PasswordUpdateFailedException("user@example.com"))

        @GetMapping("/test/error")
        fun error(): Mono<String> =
            Mono.error(RuntimeException("unexpected failure"))
    }
}