// 카카오 사용자 조회와 서비스 JWT 발급을 조합하는 로그인 유스케이스
package com.safelense.auth.application

import com.safelense.auth.kakao.KakaoApiClient
import com.safelense.auth.token.RefreshTokenStore
import com.safelense.user.User
import com.safelense.user.UserRepository
import org.springframework.stereotype.Service

data class KakaoLoginResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val isNewUser: Boolean,
)

@Service
class KakaoLoginService(
    private val kakaoApiClient: KakaoApiClient,
    private val userRepository: UserRepository,
    private val tokenIssuer: JwtTokenIssuer,
    private val refreshTokenStore: RefreshTokenStore,
) {
    fun login(authorizationCode: String, redirectUri: String): KakaoLoginResult {
        val kakaoUser = kakaoApiClient.getUser(authorizationCode, redirectUri)
        val existingUser = userRepository.findByKakaoId(kakaoUser.id)
        val user = existingUser ?: userRepository.save(
            User(
                kakaoId = kakaoUser.id,
                nickname = kakaoUser.nickname,
                profileImageUrl = kakaoUser.profileImageUrl,
            ),
        )
        val tokens = tokenIssuer.issue(requireNotNull(user.id))
        refreshTokenStore.save(requireNotNull(user.id), tokens.refreshToken, tokens.refreshTokenExpiresAt)

        return KakaoLoginResult(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresIn = tokens.expiresIn,
            isNewUser = existingUser == null,
        )
    }
}
