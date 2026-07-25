// OpenAI 임베딩과 Upstage 리포트 API의 환경 변수 설정 계약을 검증하는 테스트
package com.safelense.analysis.interpretation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class OpenAiConfigurationTests {
    @Test
    fun `application configuration declares Upstage report settings without a secret default`() {
        val yaml = ClassPathResource("application.yml").inputStream.bufferedReader().use { it.readText() }

        assertThat(yaml)
            .contains(
                "upstage:",
                "\${UPSTAGE_API_KEY:}",
                "\${UPSTAGE_MODEL:solar-pro3}",
                "base-url: https://api.upstage.ai/v1",
            )
    }
}
