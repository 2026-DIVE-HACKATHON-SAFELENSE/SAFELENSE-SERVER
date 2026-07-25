// Upstage Chat Completions API 인증·모델·기본 주소 설정을 바인딩하는 속성
package com.safelense.analysis.interpretation

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.upstage")
data class UpstageProperties(
    val apiKey: String,
    val model: String = "solar-pro3",
    val baseUrl: String = "https://api.upstage.ai/v1",
)
