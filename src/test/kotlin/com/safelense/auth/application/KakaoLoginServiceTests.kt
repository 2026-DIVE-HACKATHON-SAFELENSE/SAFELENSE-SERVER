// 카카오 사용자 가입과 기존 사용자 로그인을 검증하는 테스트
package com.safelense.auth.application

import com.safelense.auth.kakao.KakaoApiClient
import com.safelense.auth.kakao.KakaoUser
import com.safelense.auth.token.RefreshTokenStore
import com.safelense.user.User
import com.safelense.user.UserRepository
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class KakaoLoginServiceTests {
    private val kakaoApiClient = mock(KakaoApiClient::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val tokenIssuer = mock(JwtTokenIssuer::class.java)
    private val refreshTokenStore = mock(RefreshTokenStore::class.java)
    private val service = KakaoLoginService(kakaoApiClient, userRepository, tokenIssuer, refreshTokenStore)
    private val refreshExpiresAt = Instant.parse("2026-08-01T00:00:00Z")

    @Test
    fun `creates a user then returns issued tokens for a new Kakao member`() {
        val kakaoUser = KakaoUser(123L, "라이언", "https://image.example.com/ryan.png")
        val savedUser = User(id = 7L, kakaoId = 123L, nickname = "라이언", profileImageUrl = kakaoUser.profileImageUrl)
        `when`(kakaoApiClient.getUser("code", "https://client.example.com/callback")).thenReturn(kakaoUser)
        `when`(userRepository.findByKakaoId(123L)).thenReturn(null)
        `when`(userRepository.save(any(User::class.java))).thenReturn(savedUser)
        `when`(tokenIssuer.issue(7L)).thenReturn(IssuedTokens("access", "refresh", 1800, refreshExpiresAt))

        val result = service.login("code", "https://client.example.com/callback")

        assertThat(result.isNewUser).isTrue()
        assertThat(result.accessToken).isEqualTo("access")
        verify(userRepository).save(any(User::class.java))
        verify(refreshTokenStore).save(7L, "refresh", refreshExpiresAt)
    }

    @Test
    fun `returns issued tokens for an existing Kakao member`() {
        val existingUser = User(id = 7L, kakaoId = 123L, nickname = "라이언", profileImageUrl = null)
        `when`(kakaoApiClient.getUser("code", "https://client.example.com/callback"))
            .thenReturn(KakaoUser(123L, "라이언", null))
        `when`(userRepository.findByKakaoId(123L)).thenReturn(existingUser)
        `when`(tokenIssuer.issue(7L)).thenReturn(IssuedTokens("access", "refresh", 1800, refreshExpiresAt))

        val result = service.login("code", "https://client.example.com/callback")

        assertThat(result.isNewUser).isFalse()
        assertThat(result.refreshToken).isEqualTo("refresh")
        verify(refreshTokenStore).save(7L, "refresh", refreshExpiresAt)
    }
}
