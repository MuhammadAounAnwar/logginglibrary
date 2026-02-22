package com.ono.logginglibrary.exception

import com.ono.logginglibrary.autoconfigure.exception.DefaultServletExceptionHandler
import com.ono.logginglibrary.core.exception.ErrorResponse
import com.ono.logginglibrary.core.exception.OnoBusinessException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

class DefaultServletExceptionHandlerTest {

    private class UserNotFoundException(userId: String) : OnoBusinessException(
        errorCode = "USER_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "User not found: $userId"
    )

    private class BadRequestException(detail: String) : OnoBusinessException(
        errorCode = "BAD_REQUEST",
        httpStatus = HttpStatus.BAD_REQUEST,
        message = detail
    )

    @RestController
    class TestController {
        @GetMapping("/test/users/{id}")
        fun getUser(@PathVariable id: String): String = throw UserNotFoundException(id)

        @GetMapping("/test/bad")
        fun bad(): String = throw BadRequestException("Invalid input")

        @GetMapping("/test/error")
        fun error(): String = throw RuntimeException("unexpected failure")
    }

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(TestController())
        .setControllerAdvice(DefaultServletExceptionHandler())
        .build()

    @Test
    fun `UserNotFoundException returns 404`() {
        mockMvc.get("/test/users/42")
            .andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.status") { value(404) }
                jsonPath("$.message") { value("User not found: 42") }
                jsonPath("$.path") { value("/test/users/42") }
            }
    }

    @Test
    fun `BadRequestException returns 400`() {
        mockMvc.get("/test/bad")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.status") { value(400) }
                jsonPath("$.message") { value("Invalid input") }
            }
    }

    @Test
    fun `unhandled exception returns 500`() {
        mockMvc.get("/test/error")
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.status") { value(500) }
                jsonPath("$.message") { value("Internal server error") }
            }
    }

    @Test
    fun `response contains path and timestamp`() {
        mockMvc.get("/test/bad")
            .andExpect {
                jsonPath("$.path") { value("/test/bad") }
                jsonPath("$.timestamp") { exists() }
            }
    }
}
