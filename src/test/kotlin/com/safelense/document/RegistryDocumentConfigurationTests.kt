// 등기부 S3 버킷·KMS 키·리전·보존 기간 설정 계약을 검증하는 테스트
package com.safelense.document

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class RegistryDocumentConfigurationTests {
    @Test
    fun `application configuration declares encrypted registry document storage`() {
        val yaml = ClassPathResource("application.yml").inputStream.bufferedReader().use { it.readText() }

        assertThat(yaml)
            .contains(
                "registry-document:",
                "\${REGISTRY_DOCUMENT_BUCKET:}",
                "\${REGISTRY_DOCUMENT_KMS_KEY_ID:}",
                "\${AWS_REGION:ap-northeast-2}",
                "retention: P30D",
            )
    }
}
