// Swagger 인증과 API 설명 메타데이터 계약을 검증하는 테스트
package com.safelense.openapi

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiDocumentationContractTests {
    @Test
    fun `documents JWT bearer authentication and the Safelense API`() {
        val configuration = Files.readString(Path.of("src/main/kotlin/com/safelense/openapi/OpenApiConfig.kt"))

        assertThat(configuration).contains(
            "@SecurityScheme",
            "bearerAuth",
            "scheme = \"bearer\"",
            "SAFELENSE API",
        )
    }

    @Test
    fun `adds tags and operations to public API controllers`() {
        val controllers = listOf(
            "AnalysisCaseController.kt",
            "AnalysisChecklistController.kt",
            "AnalysisDocumentController.kt",
            "AnalysisExecutionController.kt",
            "AnalysisResultController.kt",
            "AnalysisTemplateController.kt",
        ).map { Files.readString(Path.of("src/main/kotlin/com/safelense/analysis", it)) } + listOf(
            Files.readString(Path.of("src/main/kotlin/com/safelense/auth/presentation/KakaoAuthController.kt")),
            Files.readString(Path.of("src/main/kotlin/com/safelense/notification/NotificationController.kt")),
            Files.readString(Path.of("src/main/kotlin/com/safelense/property/HomePropertyController.kt")),
            Files.readString(Path.of("src/main/kotlin/com/safelense/user/UserController.kt")),
        )

        controllers.forEach { controller ->
            assertThat(controller).contains("@Tag(", "@Operation(")
        }
    }
}
