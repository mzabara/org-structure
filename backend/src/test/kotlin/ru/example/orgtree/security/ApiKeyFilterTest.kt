package ru.example.orgtree.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/** Отдельно фиксирует запрет анонимного доступа вне явно выбранного dev-профиля. */
class ApiKeyFilterTest {
    @Test
    fun `пустые ключи вне dev не открывают API`() {
        val filter = ApiKeyFilter(ApiKeyProperties(), MockEnvironment(), jacksonObjectMapper())
        val response = MockHttpServletResponse()
        var called = false
        filter.doFilter(MockHttpServletRequest("GET", "/api/v1/employees"), response) { _, _ -> called = true }
        assertThat(called).isFalse()
        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `явный dev разрешает локальный UI без ключа`() {
        val filter = ApiKeyFilter(ApiKeyProperties(), MockEnvironment().withProperty("spring.profiles.active", "dev").apply {
            setActiveProfiles("dev")
        }, jacksonObjectMapper())
        var called = false
        filter.doFilter(MockHttpServletRequest("GET", "/api/v1/employees"), MockHttpServletResponse()) { _, _ -> called = true }
        assertThat(called).isTrue()
    }
}
