// OpenAI 키·모델·Responses API 기본 주소의 환경 변수 설정 계약을 검증하는 테스트
package com.safelense.analysis.interpretation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class OpenAiConfigurationTests {
    @Test
    fun `application configuration declares OpenAI response settings without a secret default`() {
        val yaml = ClassPathResource("application.yml").inputStream.bufferedReader().use { it.readText() }

        assertThat(yaml)
            .contains(
                "openai:",
                "\${OPENAI_API_KEY:}",
                "\${OPENAI_MODEL:gpt-5.6}",
                "base-url: https://api.openai.com/v1",
            )
    }
}
