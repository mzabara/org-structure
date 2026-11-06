package ru.example.orgtree.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/** Защищает весь внешний API; только явно выбранный dev отключает проверку ключа. */
@Component
class ApiKeyFilter(properties: ApiKeyProperties, environment: Environment, private val json: ObjectMapper) :
    OncePerRequestFilter() {
    private val dev = environment.acceptsProfiles(Profiles.of("dev"))
    private val digests = properties.apiKeys.filter { it.isNotBlank() }.map(::digest)

    override fun shouldNotFilter(request: HttpServletRequest) =
        dev || !(request.requestURI == "/api/v1" || request.requestURI.startsWith("/api/v1/"))

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val supplied = digest(request.getHeader("X-API-Key").orEmpty())
        // Проверяем все ключи одинаковой длины без раннего выхода при совпадении.
        val valid = digests.fold(false) { found, expected -> MessageDigest.isEqual(expected, supplied) or found }
        if (valid) {
            chain.doFilter(request, response)
            return
        }
        response.status = 401
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"
        json.writeValue(
            response.outputStream,
            mapOf(
                "type" to "about:blank", "title" to "Требуется API-ключ", "status" to 401,
                "detail" to "Передайте действительный ключ в заголовке X-API-Key.",
                "instance" to request.requestURI,
            ),
        )
    }

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
