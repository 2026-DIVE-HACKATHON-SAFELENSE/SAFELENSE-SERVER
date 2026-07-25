// prod 환경의 SSM Parameter Store 설정 선행 로딩을 검증하는 테스트
package com.safelense.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.SpringApplication
import org.springframework.core.env.StandardEnvironment

class SsmEnvironmentPostProcessorTests {
    private val reader = RecordingSsmParameterReader()
    private var readerFactoryCalls = 0
    private val processor = SsmEnvironmentPostProcessor {
        readerFactoryCalls += 1
        reader
    }

    @Test
    fun `does not create an SSM reader outside prod`() {
        val environment = StandardEnvironment().apply { setActiveProfiles("local") }

        processor.postProcessEnvironment(environment, mock(SpringApplication::class.java))

        assertThat(readerFactoryCalls).isZero()
        assertThat(reader.requests).isEmpty()
        assertThat(environment.getProperty("DB_URL")).isNull()
    }

    @Test
    fun `loads all required prod parameters as existing environment keys`() {
        val environment = StandardEnvironment().apply { setActiveProfiles("prod") }

        processor.postProcessEnvironment(environment, mock(SpringApplication::class.java))

        assertThat(readerFactoryCalls).isOne()
        assertThat(reader.requests).containsExactly(
            SsmEnvironmentPostProcessor.parameterNames.take(10),
            SsmEnvironmentPostProcessor.parameterNames.drop(10),
        )
        assertThat(environment.getProperty("DB_URL")).isEqualTo("jdbc:postgresql://example/db")
        assertThat(environment.getProperty("JWT_SECRET")).isEqualTo("secret")
        assertThat(environment.getProperty("OPENAI_API_KEY")).isEqualTo("openai-secret")
        assertThat(environment.getProperty("REGISTRY_DOCUMENT_BUCKET")).isEqualTo("registry-bucket")
        assertThat(environment.getProperty("REGISTRY_DOCUMENT_KMS_KEY_ID")).isEqualTo("kms-key")
        assertThat(environment.getProperty("PUBLIC_DATA_SERVICE_KEY")).isEqualTo("public-data-key")
        assertThat(environment.getProperty("VWORLD_API_KEY")).isEqualTo("vworld-key")
        assertThat(environment.propertySources.iterator().next().name).isEqualTo("ssmParameters")
    }

    @Test
    fun `fails prod startup when a required parameter is missing`() {
        reader.values.remove("JWT_SECRET")
        val environment = StandardEnvironment().apply { setActiveProfiles("prod") }

        assertThatThrownBy {
            processor.postProcessEnvironment(environment, mock(SpringApplication::class.java))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Required SSM parameters are unavailable")
    }

    private class RecordingSsmParameterReader : SsmParameterReader {
        val requests = mutableListOf<List<String>>()
        val values = mapOf(
            "DB_URL" to "jdbc:postgresql://example/db",
            "DB_USERNAME" to "user",
            "DB_PASSWORD" to "password",
            "KAKAO_REST_API_KEY" to "key",
            "KAKAO_CLIENT_SECRET" to "client-secret",
            "JWT_SECRET" to "secret",
            "JWT_ACCESS_TOKEN_TTL" to "PT30M",
            "JWT_REFRESH_TOKEN_TTL" to "P14D",
            "OPENAI_API_KEY" to "openai-secret",
            "REGISTRY_DOCUMENT_BUCKET" to "registry-bucket",
            "REGISTRY_DOCUMENT_KMS_KEY_ID" to "kms-key",
            "PUBLIC_DATA_SERVICE_KEY" to "public-data-key",
            "VWORLD_API_KEY" to "vworld-key",
        ).toMutableMap()

        override fun read(parameterNames: List<String>): Map<String, String> {
            requests += parameterNames
            val requestedKeys = parameterNames.map { it.substringAfterLast('/') }.toSet()
            return values.filterKeys { it in requestedKeys }
        }
    }
}
