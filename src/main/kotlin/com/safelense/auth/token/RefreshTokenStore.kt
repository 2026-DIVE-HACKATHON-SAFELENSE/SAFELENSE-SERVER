// 리프레시 토큰 해시를 저장하고 현재 활성 토큰인지 검증하는 서비스
package com.safelense.auth.token

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class RefreshTokenStore(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    fun save(userId: Long, refreshToken: String, expiresAt: Instant) {
        val tokenHash = hash(refreshToken)
        val existingToken = refreshTokenRepository.findByUserId(userId)
        val token = existingToken?.apply {
            this.tokenHash = tokenHash
            this.expiresAt = expiresAt
        } ?: RefreshToken(userId = userId, tokenHash = tokenHash, expiresAt = expiresAt)

        refreshTokenRepository.save(token)
    }

    fun matches(userId: Long, refreshToken: String): Boolean =
        refreshTokenRepository.findByUserId(userId)?.tokenHash == hash(refreshToken)

    fun deleteByUserId(userId: Long) {
        refreshTokenRepository.deleteByUserId(userId)
    }

    private fun hash(token: String): String =
        java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8)))
}
