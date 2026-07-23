// 서비스 JWT 발급기의 claim과 만료 시간을 검증하는 테스트
package com.safelense.auth.application

import com.safelense.auth.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtTokenIssuerTests {
    private val signingSecret = "01234567890123456789012345678901"
    private val properties = JwtProperties(
        signingSecret = signingSecret,
        accessTokenTtl = Duration.ofMinutes(30),
        refreshTokenTtl = Duration.ofDays(14),
    )
    private val issuer = JwtTokenIssuer(properties)

    @Test
    fun `issues access and refresh tokens for a user`() {
        val tokens = issuer.issue(42L)
        val key = Keys.hmacShaKeyFor(signingSecret.toByteArray(StandardCharsets.UTF_8))
        val accessClaims = Jwts.parser().verifyWith(key).build().parseSignedClaims(tokens.accessToken).payload
        val refreshClaims = Jwts.parser().verifyWith(key).build().parseSignedClaims(tokens.refreshToken).payload

        assertThat(accessClaims.subject).isEqualTo("42")
        assertThat(accessClaims["tokenType"]).isEqualTo("access")
        assertThat(refreshClaims.subject).isEqualTo("42")
        assertThat(refreshClaims["tokenType"]).isEqualTo("refresh")
        assertThat(tokens.expiresIn).isEqualTo(Duration.ofMinutes(30).seconds)
    }
}
