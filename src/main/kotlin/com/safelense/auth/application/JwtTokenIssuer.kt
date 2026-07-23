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
)

data class IssuedAccessToken(
    val accessToken: String,
    val expiresIn: Long,
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
        )
    }

    fun refresh(refreshToken: String): IssuedAccessToken {
        val claims = try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(refreshToken).payload
        } catch (_: JwtException) {
            throw InvalidRefreshTokenException()
        }
        if (claims["tokenType"] != "refresh") {
            throw InvalidRefreshTokenException()
        }
        val userId = claims.subject.toLongOrNull() ?: throw InvalidRefreshTokenException()
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl)

        return IssuedAccessToken(
            accessToken = createToken(userId, "access", issuedAt, expiresAt),
            expiresIn = jwtProperties.accessTokenTtl.seconds,
        )
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
