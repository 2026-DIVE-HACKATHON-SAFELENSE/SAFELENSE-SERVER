// 내 정보 조회와 온보딩 상태 변경의 HTTP 계약을 검증하는 테스트
package com.safelense.user

import com.safelense.auth.presentation.ApiExceptionHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class UserControllerTests {
    private val service = mock(UserService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(UserController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `gets the authenticated user profile`() {
        `when`(service.get(7L)).thenReturn(userView(false))

        mockMvc.perform(get("/api/v1/me").principal(authentication()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.nickname").value("세입자"))
            .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/profile.png"))
            .andExpect(jsonPath("$.onboardingCompleted").value(false))
    }

    @Test
    fun `updates onboarding state`() {
        `when`(service.updateOnboarding(7L, true)).thenReturn(userView(true))

        mockMvc.perform(
            patch("/api/v1/me/onboarding")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"onboardingCompleted":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.onboardingCompleted").value(true))

        verify(service).updateOnboarding(7L, true)
    }

    @Test
    fun `rejects missing and malformed onboarding values`() {
        listOf("{}", """{"onboardingCompleted":"yes"}""").forEach { body ->
            mockMvc.perform(
                patch("/api/v1/me/onboarding")
                    .principal(authentication())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `returns not found when the authenticated user does not exist`() {
        `when`(service.get(7L)).thenThrow(UserNotFoundException())

        mockMvc.perform(get("/api/v1/me").principal(authentication()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
    }

    private fun userView(onboardingCompleted: Boolean) =
        UserView(
            id = 7L,
            nickname = "세입자",
            profileImageUrl = "https://example.com/profile.png",
            onboardingCompleted = onboardingCompleted,
        )

    private fun authentication() = UsernamePasswordAuthenticationToken(7L, null)
}
