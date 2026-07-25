// 저장된 분석 결과로 PDF 리포트가 생성되는지 검증하는 테스트
package com.safelense.analysis

import com.safelense.analysis.interpretation.EvidenceBackedStatement
import com.safelense.analysis.report.AiInterpretationReport
import com.safelense.analysis.report.ContractDecisionReportView
import com.safelense.analysis.report.ContractSafetyReport
import com.safelense.analysis.run.AnalysisDataMode
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnalysisReportServiceTests {
    private val service = AnalysisReportService()

    @Test
    fun `creates a pdf report from stored analysis detail`() {
        val bytes = service.create(detail())

        assertThat(bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)).isEqualTo("%PDF-")
        assertThat(bytes.size).isGreaterThan(500)
    }

    @Test
    fun `creates a pdf report from a stored contract decision snapshot`() {
        val bytes = service.create(
            ContractDecisionReportView(
                contractSafety = ContractSafetyReport(
                    grade = AnalysisRiskGrade.UNKNOWN,
                    confidence = 35,
                    summary = "추가 확인이 필요합니다.",
                ),
                aiInterpretation = AiInterpretationReport(
                    EvidenceBackedStatement("가격 근거를 확인하세요.", listOf("evidence-11")),
                    fallback = false,
                ),
                actionGuide = listOf(EvidenceBackedStatement("공시가격을 대조하세요.", listOf("evidence-11"))),
                dataMode = AnalysisDataMode.DEMO,
                asOf = "2026-07-01T00:00:00Z",
            ),
        )

        assertThat(bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)).isEqualTo("%PDF-")
        assertThat(bytes.size).isGreaterThan(500)
    }

    private fun detail() =
        AnalysisResultDetail(
            id = 31L,
            caseId = 11L,
            propertyId = 5L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            score = 45,
            grade = AnalysisRiskGrade.MEDIUM,
            confidence = 70,
            summary = "확인이 필요한 위험 신호가 있습니다.",
            findings = listOf("임대인 신원 확인이 필요합니다."),
            recommendations = listOf("등기상 소유자와 계약 상대방을 대조하세요."),
            ruleVersion = "2026-07-24-v1",
            analyzedAt = Instant.parse("2026-07-24T10:15:30Z"),
        )
}
