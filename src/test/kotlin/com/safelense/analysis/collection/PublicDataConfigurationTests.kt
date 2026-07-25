// 실제 공공데이터와 임베딩 설정의 환경변수 바인딩을 검증하는 테스트
package com.safelense.analysis.collection

import com.safelense.analysis.interpretation.OpenAiProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class PublicDataConfigurationTests {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfiguration::class.java)
        .withPropertyValues(
            "app.public-data.service-key=public-key",
            "app.vworld.api-key=vworld-key",
            "app.openai.api-key=openai-key",
            "app.openai.embedding-model=text-embedding-3-small",
        )

    @Test
    fun `binds provider keys and embedding model`() {
        contextRunner.run { context ->
            assertThat(context.getBean(PublicDataProperties::class.java).serviceKey)
                .isEqualTo("public-key")
            assertThat(context.getBean(VWorldProperties::class.java).apiKey)
                .isEqualTo("vworld-key")
            assertThat(context.getBean(OpenAiProperties::class.java).embeddingModel)
                .isEqualTo("text-embedding-3-small")
        }
    }

    @Test
    fun `keeps the existing positional base URL contract`() {
        val properties = OpenAiProperties("openai-key", "gpt-5.6", "https://openai.test/v1")

        assertThat(properties.baseUrl).isEqualTo("https://openai.test/v1")
        assertThat(properties.embeddingModel).isEqualTo("text-embedding-3-small")
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(
        PublicDataProperties::class,
        VWorldProperties::class,
        OpenAiProperties::class,
    )
    private class PropertiesConfiguration
}
