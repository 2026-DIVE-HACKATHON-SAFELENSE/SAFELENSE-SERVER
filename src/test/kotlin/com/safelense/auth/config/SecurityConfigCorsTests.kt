// Spring Security CORS 허용 정책을 검증하는 테스트
package com.safelense.auth.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.filter.CorsFilter

class SecurityConfigCorsTests {
    private val config = SecurityConfig(
        jwtAuthenticationFilter = mock(JwtAuthenticationFilter::class.java),
        allowedOrigins = listOf(
            "http://localhost:8081",
            "https://safelense-fe.pages.dev",
            "https://safelense.site",
        ),
    )
    private val corsFilter = CorsFilter(config.corsConfigurationSource())

    @Test
    fun `allows a configured origin preflight with API headers`() {
        val request = preflight("https://safelense.site")
        val response = MockHttpServletResponse()

        corsFilter.doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(HttpStatus.OK.value())
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .isEqualTo("https://safelense.site")
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST")
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).contains("Authorization")
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull()
    }

    @Test
    fun `rejects a preflight from an unconfigured origin`() {
        val response = MockHttpServletResponse()

        corsFilter.doFilter(preflight("https://untrusted.example"), response, MockFilterChain())

        assertThat(response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull()
    }

    private fun preflight(origin: String) =
        MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/v1/analysis-cases/11/analyze").apply {
            addHeader(HttpHeaders.ORIGIN, origin)
            addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
            addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                "Authorization, Content-Type, Idempotency-Key",
            )
        }
}
