// 서비스 JWT 서명과 만료 시간을 보관하는 구성 속성
package com.safelense.auth.config

import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.crypto.SecretKey
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth.jwt")
data class JwtProperties(
    val signingSecret: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
) {
    fun signingKey(): SecretKey {
        val secretBytes = signingSecret.toByteArray(StandardCharsets.UTF_8)
        require(secretBytes.size >= 32) { "JWT signing secret must be at least 32 bytes." }
        return Keys.hmacShaKeyFor(secretBytes)
    }
}
