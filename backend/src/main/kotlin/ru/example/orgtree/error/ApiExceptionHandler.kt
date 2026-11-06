package ru.example.orgtree.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

/** Не раскрывает внутренние исключения и унифицирует ошибки в application/problem+json. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {
    @ExceptionHandler(EntityNotFoundException::class)
    fun notFound(error: EntityNotFoundException, request: HttpServletRequest): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "${error.entity} не найден", error.message.orEmpty(), request)

    @ExceptionHandler(
        HandlerMethodValidationException::class, ConstraintViolationException::class,
        MethodArgumentTypeMismatchException::class, MissingServletRequestParameterException::class,
        IllegalArgumentException::class,
    )
    fun badRequest(error: Exception, request: HttpServletRequest): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Некорректные параметры запроса", "Проверьте типы и допустимые значения параметров.", request)

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun routeNotFound(error: Exception, request: HttpServletRequest): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Ресурс не найден", "Запрошенный адрес не существует.", request)

    @ExceptionHandler(ErrorResponseException::class)
    fun frameworkError(error: ErrorResponseException, request: HttpServletRequest): ProblemDetail =
        problem(HttpStatus.valueOf(error.statusCode.value()), "Запрос не выполнен", "Проверьте запрос.", request)

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception, request: HttpServletRequest): ProblemDetail =
        problem(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", "Не удалось выполнить запрос. Повторите попытку позже.", request)

    private fun problem(status: HttpStatus, title: String, detail: String, request: HttpServletRequest) =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
            instance = URI.create(request.requestURI)
        }
}
