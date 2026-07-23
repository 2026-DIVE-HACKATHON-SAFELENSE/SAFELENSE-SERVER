// 카카오 REST API 호출에 필요한 환경 기반 구성 속성
package com.safelense.auth.kakao

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth.kakao")
data class KakaoProperties(
    val restApiKey: String,
    val clientSecret: String,
    val tokenUri: String = "https://kauth.kakao.com/oauth/token",
    val userInfoUri: String = "https://kapi.kakao.com/v2/user/me",
)
