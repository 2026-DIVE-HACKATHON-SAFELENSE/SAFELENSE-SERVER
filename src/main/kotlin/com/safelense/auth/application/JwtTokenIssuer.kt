// 내부 사용자 식별자로 서비스 JWT 쌍을 발급하는 컴포넌트
package com.safelense.auth.application

import com.safelense.auth.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.JwtException
import java.time.Instant
import java.util.Date
import org.springframework.stereotype.Component

data class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val refreshTokenExpiresAt: Instant,
)

@Component
class JwtTokenIssuer(
    private val jwtProperties: JwtProperties,
) {
    private val signingKey = jwtProperties.signingKey()

    fun issue(userId: Long): IssuedTokens {
        val issuedAt = Instant.now()
        val accessExpiresAt = issuedAt.plus(jwtProperties.accessTokenTtl)
        val refreshExpiresAt = issuedAt.plus(jwtProperties.refreshTokenTtl)

        return IssuedTokens(
            accessToken = createToken(userId, "access", issuedAt, accessExpiresAt),
            refreshToken = createToken(userId, "refresh", issuedAt, refreshExpiresAt),
            expiresIn = jwtProperties.accessTokenTtl.seconds,
            refreshTokenExpiresAt = refreshExpiresAt,
        )
    }

    fun validateRefreshToken(refreshToken: String): Long {
        return validateToken(refreshToken, "refresh") { InvalidRefreshTokenException() }
    }

    fun validateAccessToken(accessToken: String): Long {
        return validateToken(accessToken, "access") { InvalidAccessTokenException() }
    }

    private fun validateToken(
        token: String,
        tokenType: String,
        invalidTokenException: () -> RuntimeException,
    ): Long {
        val claims = try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).payload
        } catch (_: JwtException) {
            throw invalidTokenException()
        }
        if (claims["tokenType"] != tokenType) {
            throw invalidTokenException()
        }

        return claims.subject.toLongOrNull() ?: throw invalidTokenException()
    }

    private fun createToken(
        userId: Long,
        tokenType: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String = Jwts.builder()
        .subject(userId.toString())
        .claim("tokenType", tokenType)
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiresAt))
        .signWith(signingKey)
        .compact()
}
