// 애플리케이션 CORS 허용 출처 목록을 바인딩하는 설정 객체
package com.safelense.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
