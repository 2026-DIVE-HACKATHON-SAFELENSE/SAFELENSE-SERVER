// 로그아웃 시 활성 리프레시 토큰을 삭제하는지 확인하는 테스트
package com.safelense.auth.application

import com.safelense.auth.token.RefreshTokenStore
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class LogoutServiceTests {
    private val refreshTokenStore = mock(RefreshTokenStore::class.java)
    private val logoutService = LogoutService(refreshTokenStore)

    @Test
    fun `deletes the current users refresh token`() {
        logoutService.logout(7L)

        verify(refreshTokenStore).deleteByUserId(7L)
    }
}
