// OpenAI Responses API 인증·모델·기본 주소 설정을 바인딩하는 속성
package com.safelense.analysis.interpretation

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.openai")
data class OpenAiProperties(
    val apiKey: String,
    val model: String = "gpt-5.6",
    val baseUrl: String = "https://api.openai.com/v1",
    val embeddingModel: String = "text-embedding-3-small",
)
