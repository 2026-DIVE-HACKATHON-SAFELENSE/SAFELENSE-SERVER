// 카카오 인가 코드로 서비스 사용자 정보를 조회하는 포트
package com.safelense.auth.kakao

data class KakaoUser(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
)

interface KakaoApiClient {
    fun getUser(authorizationCode: String, redirectUri: String): KakaoUser
}
