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

    @Test
    fun `fully documents every pivot API operation and response`() {
        val controllers = mapOf(
            "src/main/kotlin/com/safelense/property/PropertiesController.kt" to 4,
            "src/main/kotlin/com/safelense/document/RegistryDocumentController.kt" to 2,
            "src/main/kotlin/com/safelense/analysis/run/AnalysisRunController.kt" to 3,
            "src/main/kotlin/com/safelense/analysis/AnalysisResultController.kt" to 3,
        )

        controllers.forEach { (path, operationCount) ->
            val controller = Files.readString(Path.of(path))

            assertThat(controller).contains("@Tag(")
            assertThat(controller.occurrences("@Operation(")).isEqualTo(operationCount)
            assertThat(controller.occurrences("@ApiResponses(")).isEqualTo(operationCount)
        }
    }

    @Test
    fun `documents pivot parameters request response and error schemas`() {
        val properties = Files.readString(Path.of("src/main/kotlin/com/safelense/property/PropertiesController.kt"))
        val documents = Files.readString(Path.of("src/main/kotlin/com/safelense/document/RegistryDocumentController.kt"))
        val runs = Files.readString(Path.of("src/main/kotlin/com/safelense/analysis/run/AnalysisRunController.kt"))
        val results = Files.readString(Path.of("src/main/kotlin/com/safelense/analysis/AnalysisResultController.kt"))
        val homePropertyViews = Files.readString(Path.of("src/main/kotlin/com/safelense/property/HomePropertyController.kt"))
        val documentViews = Files.readString(Path.of("src/main/kotlin/com/safelense/document/RegistryDocumentService.kt"))
        val runViews = Files.readString(Path.of("src/main/kotlin/com/safelense/analysis/run/AnalysisRunService.kt"))
        val resultViews = Files.readString(Path.of("src/main/kotlin/com/safelense/analysis/AnalysisResultService.kt"))
        val contractReport = Files.readString(Path.of("src/main/kotlin/com/safelense/analysis/report/ContractDecisionReportService.kt"))
        val evidenceStatement = Files.readString(Path.of("src/main/kotlin/com/safelense/analysis/interpretation/ReportEvidenceValidator.kt"))
        val errors = Files.readString(Path.of("src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt"))

        assertThat(properties).contains(
            "@Schema(description = \"후보 매물 목록 응답\")",
            "@Parameter(description = \"후보 매물 ID\"",
        )
        assertThat(documents).contains(
            "@Schema(description = \"등기부 원본 업로드 응답\")",
            "@Parameter(description = \"PDF 형식의 등기부 원본\"",
        )
        assertThat(runs).contains(
            "@Schema(description = \"계약 전 분석 실행 요청\")",
            "@Parameter(description = \"동일 분석 요청을 식별하는 멱등 키\"",
        )
        assertThat(results).contains(
            "description = \"결과 형식 구분.",
            "oneOf = [AnalysisResultDetail::class, ContractDecisionReportView::class]",
            "format = \"binary\"",
        )
        assertThat(homePropertyViews.occurrences("@field:Schema(")).isEqualTo(12)
        assertThat(documentViews).contains("@Schema(description = \"등기부 원본 메타데이터\")")
        assertThat(runViews).contains(
            "@Schema(description = \"계약 전 분석 실행 상태\")",
            "@Schema(description = \"계약 전 분석 실행 이력\")",
        )
        assertThat(resultViews.occurrences("@field:Schema(")).isEqualTo(24)
        assertThat(contractReport).contains(
            "@Schema(description = \"계약 안전성 분석\")",
            "@Schema(description = \"근거 기반 계약 의사결정 리포트\")",
        )
        assertThat(evidenceStatement).contains("@Schema(description = \"수집 근거를 인용하는 리포트 문장\")")
        assertThat(errors).contains("@Schema(description = \"API 오류 응답\")")
    }

    private fun String.occurrences(value: String): Int = windowed(value.length).count { it == value }
}
