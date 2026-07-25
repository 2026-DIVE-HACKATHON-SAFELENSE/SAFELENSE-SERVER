// 카카오 HTTP API 클라이언트가 주입받는 RestClient 빌더를 제공하는 설정
package com.safelense.auth.kakao

import java.time.Duration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
class RestClientConfig {
    @Bean
    fun restClientBuilder(): RestClient.Builder {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(3))
            setReadTimeout(Duration.ofSeconds(10))
        }
        return RestClient.builder().requestFactory(requestFactory)
    }
}
