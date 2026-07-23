// Bearer 액세스 JWT를 Spring Security 인증으로 변환하는 필터 테스트
package com.safelense.auth.config

import com.safelense.auth.application.JwtTokenIssuer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthenticationFilterTests {
    private val tokenIssuer = mock(JwtTokenIssuer::class.java)
    private val filter = JwtAuthenticationFilter(tokenIssuer)

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `sets the user ID as authentication principal for a bearer access token`() {
        `when`(tokenIssuer.validateAccessToken("access-token")).thenReturn(7L)
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer access-token") }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication?.principal).isEqualTo(7L)
    }
}
