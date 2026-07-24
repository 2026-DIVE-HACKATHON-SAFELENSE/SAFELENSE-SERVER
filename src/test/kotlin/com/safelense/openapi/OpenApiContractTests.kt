// Springdoc 의존성, HTTPS forwarded header, 공개 문서 경로 계약을 검증하는 테스트
package com.safelense.openapi

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiContractTests {
    @Test
    fun `uses the Spring Boot 4 compatible Swagger UI starter`() {
        assertThat(Files.readString(Path.of("build.gradle.kts")))
            .contains("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    }

    @Test
    fun `honors HTTPS proxy headers and exposes only documentation without authentication`() {
        val application = Files.readString(Path.of("src/main/resources/application.yml"))
        val security = Files.readString(Path.of("src/main/kotlin/com/safelense/auth/config/SecurityConfig.kt"))

        assertThat(application).contains("forward-headers-strategy: framework")
        assertThat(security).contains("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
    }
}
