package com.ono.logginglibrary.exception

abstract class OnoBusinessException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)


class NoSuchUserException(message: String) : OnoBusinessException(message)
class TokenExpiredException(message: String) : OnoBusinessException(message)
class TokenNotFoundException(message: String) : OnoBusinessException(message)
class InvalidTokenException(message: String) : OnoBusinessException(message)

class InvalidCredentialsException : OnoBusinessException(
    "Invalid email or password"
)

class PasswordUpdateFailedException(email: String) : OnoBusinessException(
    "Failed to update password for user: $email"
)
