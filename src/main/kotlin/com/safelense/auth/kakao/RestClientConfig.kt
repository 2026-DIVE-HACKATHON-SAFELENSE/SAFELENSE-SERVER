// 카카오 HTTP API 클라이언트가 주입받는 RestClient 빌더를 제공하는 설정
package com.safelense.auth.kakao

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
class RestClientConfig {
    @Bean
    fun restClientBuilder(): RestClient.Builder = RestClient.builder()
}
