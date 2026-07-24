// YAML CORS 허용 출처 목록이 설정 객체에 바인딩되는지 검증하는 테스트
package com.safelense.auth.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class CorsPropertiesTests {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(CorsPropertiesConfiguration::class.java)
            .withPropertyValues(
                "app.cors.allowed-origins[0]=http://localhost:8081",
                "app.cors.allowed-origins[1]=https://safelense-fe.pages.dev",
                "app.cors.allowed-origins[2]=https://safelense.site",
            )

    @Test
    fun `binds configured origins in order`() {
        contextRunner.run { context ->
            assertThat(context.getBean(CorsProperties::class.java).allowedOrigins).containsExactly(
                "http://localhost:8081",
                "https://safelense-fe.pages.dev",
                "https://safelense.site",
            )
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties::class)
    private class CorsPropertiesConfiguration
}
