// 리프레시 토큰의 저장 검증과 교체 발급을 확인하는 테스트
package com.safelense.auth.application

import com.safelense.auth.token.RefreshTokenStore
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class TokenRefreshServiceTests {
    private val tokenIssuer = mock(JwtTokenIssuer::class.java)
    private val refreshTokenStore = mock(RefreshTokenStore::class.java)
    private val service = TokenRefreshService(tokenIssuer, refreshTokenStore)
    private val refreshExpiresAt = Instant.parse("2026-08-01T00:00:00Z")

    @Test
    fun `issues and stores a new token pair for the active refresh token`() {
        `when`(tokenIssuer.validateRefreshToken("old-refresh")).thenReturn(7L)
        `when`(refreshTokenStore.matches(7L, "old-refresh")).thenReturn(true)
        `when`(tokenIssuer.issue(7L)).thenReturn(IssuedTokens("new-access", "new-refresh", 1800, refreshExpiresAt))

        val result = service.refresh("old-refresh")

        assertThat(result.accessToken).isEqualTo("new-access")
        assertThat(result.refreshToken).isEqualTo("new-refresh")
        assertThat(result.expiresIn).isEqualTo(1800)
        verify(refreshTokenStore).save(7L, "new-refresh", refreshExpiresAt)
    }

    @Test
    fun `rejects a refresh token that is not stored`() {
        `when`(tokenIssuer.validateRefreshToken("removed-refresh")).thenReturn(7L)
        `when`(refreshTokenStore.matches(7L, "removed-refresh")).thenReturn(false)

        assertThatThrownBy { service.refresh("removed-refresh") }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
    }
}
