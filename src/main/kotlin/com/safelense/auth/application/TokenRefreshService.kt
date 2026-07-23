// 리프레시 JWT를 검증해 새 액세스 JWT를 발급하는 유스케이스
package com.safelense.auth.application

import com.safelense.auth.token.RefreshTokenStore
import org.springframework.stereotype.Service

data class TokenRefreshResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

@Service
class TokenRefreshService(
    private val jwtTokenIssuer: JwtTokenIssuer,
    private val refreshTokenStore: RefreshTokenStore,
) {
    fun refresh(refreshToken: String): TokenRefreshResult {
        val userId = jwtTokenIssuer.validateRefreshToken(refreshToken)
        if (!refreshTokenStore.matches(userId, refreshToken)) {
            throw InvalidRefreshTokenException()
        }
        val tokens = jwtTokenIssuer.issue(userId)
        refreshTokenStore.save(userId, tokens.refreshToken, tokens.refreshTokenExpiresAt)

        return TokenRefreshResult(tokens.accessToken, tokens.refreshToken, tokens.expiresIn)
    }
}
