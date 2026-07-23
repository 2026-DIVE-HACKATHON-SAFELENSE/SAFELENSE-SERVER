// 카카오 로그인 HTTP 요청 검증과 응답 계약을 검증하는 테스트
package com.safelense.auth.presentation

import com.safelense.auth.application.KakaoLoginResult
import com.safelense.auth.application.KakaoLoginService
import com.safelense.auth.application.InvalidRefreshTokenException
import com.safelense.auth.application.TokenRefreshResult
import com.safelense.auth.application.TokenRefreshService
import com.safelense.auth.kakao.KakaoAuthenticationException
import com.safelense.auth.kakao.KakaoApiUnavailableException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KakaoAuthControllerTests {
    private val loginService = mock(KakaoLoginService::class.java)
    private val tokenRefreshService = mock(TokenRefreshService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(KakaoAuthController(loginService, tokenRefreshService))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `returns service JWTs for a valid Kakao authorization code`() {
        `when`(loginService.login(anyString(), anyString()))
            .thenReturn(KakaoLoginResult("access-token", "refresh-token", 1800, true))

        mockMvc.perform(
            post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\":\"authorization-code\",\"redirectUri\":\"https://client.example.com/callback\"}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(1800))
            .andExpect(jsonPath("$.isNewUser").value(true))
    }

    @Test
    fun `rejects an empty authorization code`() {
        mockMvc.perform(
            post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\":\"\",\"redirectUri\":\"https://client.example.com/callback\"}"),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `returns unauthorized when Kakao rejects the authorization code`() {
        `when`(loginService.login(anyString(), anyString())).thenThrow(KakaoAuthenticationException())

        mockMvc.perform(
            post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\":\"authorization-code\",\"redirectUri\":\"https://client.example.com/callback\"}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("KAKAO_AUTHENTICATION_FAILED"))
    }

    @Test
    fun `returns bad gateway when Kakao API is unavailable`() {
        `when`(loginService.login(anyString(), anyString())).thenThrow(KakaoApiUnavailableException())

        mockMvc.perform(
            post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\":\"authorization-code\",\"redirectUri\":\"https://client.example.com/callback\"}"),
        )
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("KAKAO_API_UNAVAILABLE"))
    }

    @Test
    fun `returns a new access token for a valid refresh token`() {
        `when`(tokenRefreshService.refresh("refresh-token"))
            .thenReturn(TokenRefreshResult("new-access-token", "new-refresh-token", 1800))

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh-token\"}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("new-access-token"))
            .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(1800))
    }

    @Test
    fun `rejects an empty refresh token`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"\"}"),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `returns unauthorized for an invalid refresh token`() {
        `when`(tokenRefreshService.refresh(anyString())).thenThrow(InvalidRefreshTokenException())

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"expired-token\"}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
    }
}
