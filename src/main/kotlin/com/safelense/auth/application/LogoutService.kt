// 로그아웃한 사용자의 활성 리프레시 토큰을 폐기하는 서비스
package com.safelense.auth.application

import com.safelense.auth.token.RefreshTokenStore
import org.springframework.stereotype.Service

@Service
class LogoutService(
    private val refreshTokenStore: RefreshTokenStore,
) {
    fun logout(userId: Long) {
        refreshTokenStore.deleteByUserId(userId)
    }
}
