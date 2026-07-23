// 리프레시 토큰 해시 저장과 일치 검증을 확인하는 테스트
package com.safelense.auth.token

import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RefreshTokenStoreTests {
    private val repository = mock(RefreshTokenRepository::class.java)
    private val store = RefreshTokenStore(repository)
    private val expiresAt = Instant.parse("2026-08-01T00:00:00Z")

    @Test
    fun `stores a hash and matches only the stored refresh token`() {
        store.save(7L, "refresh-token", expiresAt)

        val tokenCaptor = ArgumentCaptor.forClass(RefreshToken::class.java)
        verify(repository).save(tokenCaptor.capture())
        val storedToken = tokenCaptor.value
        `when`(repository.findByUserId(7L)).thenReturn(storedToken)

        assertThat(storedToken.tokenHash).isNotEqualTo("refresh-token")
        assertThat(storedToken.tokenHash).hasSize(64)
        assertThat(store.matches(7L, "refresh-token")).isTrue()
        assertThat(store.matches(7L, "another-token")).isFalse()
    }

    @Test
    fun `deletes the users refresh token`() {
        store.deleteByUserId(7L)

        verify(repository).deleteByUserId(7L)
    }
}
