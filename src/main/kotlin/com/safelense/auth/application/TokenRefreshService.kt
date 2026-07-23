// 리프레시 JWT를 검증해 새 액세스 JWT를 발급하는 유스케이스
package com.safelense.auth.application

import org.springframework.stereotype.Service

data class TokenRefreshResult(
    val accessToken: String,
    val expiresIn: Long,
)

@Service
class TokenRefreshService(
    private val jwtTokenIssuer: JwtTokenIssuer,
) {
    fun refresh(refreshToken: String): TokenRefreshResult {
        val refreshedToken = jwtTokenIssuer.refresh(refreshToken)
        return TokenRefreshResult(refreshedToken.accessToken, refreshedToken.expiresIn)
    }
}
