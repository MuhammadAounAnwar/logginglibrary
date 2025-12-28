package com.ono.logginglibrary.exception

class NoSuchUserException(message: String) : RuntimeException(message)
class TokenExpiredException(message: String) : RuntimeException(message)
class TokenNotFoundException(message: String) : RuntimeException(message)