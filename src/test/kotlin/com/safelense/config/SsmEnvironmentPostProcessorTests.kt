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
        assertThat(reader.requests).containsExactly(SsmEnvironmentPostProcessor.parameterNames)
        assertThat(environment.getProperty("DB_URL")).isEqualTo("jdbc:postgresql://example/db")
        assertThat(environment.getProperty("JWT_SECRET")).isEqualTo("secret")
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
        ).toMutableMap()

        override fun read(parameterNames: List<String>): Map<String, String> {
            requests += parameterNames
            return values
        }
    }
}
