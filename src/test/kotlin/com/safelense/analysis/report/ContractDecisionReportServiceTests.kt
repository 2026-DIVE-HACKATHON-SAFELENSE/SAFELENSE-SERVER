// 실행별 계약 의사결정 리포트 스냅샷의 불변성과 소유권 격리를 검증하는 테스트
package com.safelense.analysis.report

import com.safelense.analysis.AnalysisRiskAssessment
import com.safelense.analysis.AnalysisRiskGrade
import com.safelense.analysis.evidence.CollectedEvidence
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.analysis.interpretation.AiReportResult
import com.safelense.analysis.interpretation.EvidenceBackedStatement
import com.safelense.analysis.interpretation.OpenAiProperties
import com.safelense.analysis.interpretation.OpenAiReportClient
import com.safelense.analysis.interpretation.OpenAiReportInterpreter
import com.safelense.analysis.interpretation.OpenAiReportRequest
import com.safelense.analysis.interpretation.ReportEvidenceValidator
import com.safelense.analysis.match.MatchedCase
import com.safelense.analysis.run.AnalysisDataMode
import com.safelense.analysis.run.AnalysisRun
import com.safelense.analysis.run.AnalysisRunNotFoundException
import com.safelense.analysis.run.AnalysisRunRepository
import com.safelense.analysis.run.AnalysisRunStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.databind.ObjectMapper

class ContractDecisionReportServiceTests {
    private val reportRepository = mock(AnalysisReportRepository::class.java)
    private val runRepository = mock(AnalysisRunRepository::class.java)
    private val client = CountingReportClient()
    private val service = ContractDecisionReportService(
        reportRepository,
        runRepository,
        OpenAiReportInterpreter(
            client,
            ReportEvidenceValidator(),
            OpenAiProperties("test-key", "gpt-5.6", "https://api.openai.com/v1"),
        ),
        ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `stores one immutable JSON report snapshot per analysis run`() {
        var stored: AnalysisReport? = null
        `when`(reportRepository.findByRunId(3L)).thenAnswer { stored }
        `when`(reportRepository.save(any(AnalysisReport::class.java))).thenAnswer {
            (it.arguments[0] as AnalysisReport).apply { id = 21L }.also { report -> stored = report }
        }

        val first = service.generate(run(), listOf(evidence()), emptyList(), assessment())
        val repeated = service.generate(run(), listOf(evidence()), emptyList(), assessment())

        assertThat(first.created).isTrue()
        assertThat(first.view.dataMode).isEqualTo(AnalysisDataMode.DEMO)
        assertThat(first.view.contractSafety.grade).isEqualTo(AnalysisRiskGrade.UNKNOWN)
        assertThat(first.view.dataCoverage.single().source).isEqualTo("VWORLD_OFFICIAL_PRICE")
        assertThat(repeated.created).isFalse()
        assertThat(repeated.view.aiInterpretation.summary.text)
            .isEqualTo(first.view.aiInterpretation.summary.text)
        assertThat(client.calls).isEqualTo(1)
        assertThat(stored?.reportJson).contains("\"dataMode\":\"DEMO\"")
    }

    @Test
    fun `hides a report when the analysis run is not owned by the user`() {
        `when`(runRepository.findByIdAndUserId(3L, 1L)).thenReturn(null)

        assertThatThrownBy { service.get(1L, 3L) }
            .isInstanceOf(AnalysisRunNotFoundException::class.java)
    }

    @Test
    fun `includes snapshotted similar cases in a live report`() {
        var stored: AnalysisReport? = null
        `when`(reportRepository.findByRunId(3L)).thenAnswer { stored }
        `when`(reportRepository.save(any(AnalysisReport::class.java))).thenAnswer {
            (it.arguments[0] as AnalysisReport).also { report -> stored = report }
        }
        val matchedCase = MatchedCase(
            databaseId = 101L,
            caseId = "101",
            structuredScore = 0.8,
            semanticScore = 0.9,
            combinedScore = 0.845,
            pattern = "보증금반환 · 상담",
            summary = "아파트 보증금반환 유사 사례입니다.",
        )

        val generated = service.generate(
            run(AnalysisDataMode.LIVE),
            listOf(evidence()),
            listOf(matchedCase),
            assessment(),
        )

        assertThat(generated.view.dataMode).isEqualTo(AnalysisDataMode.LIVE)
        assertThat(generated.view.similarCases).containsExactly(
            SimilarCaseReport(
                caseId = "101",
                similarity = 0.845,
                pattern = "보증금반환 · 상담",
                summary = "아파트 보증금반환 유사 사례입니다.",
            ),
        )
        assertThat(stored?.reportJson).contains("\"similarCases\"")
    }

    private fun run(dataMode: AnalysisDataMode = AnalysisDataMode.DEMO) =
        AnalysisRun(
            id = 3L,
            propertyId = 2L,
            userId = 1L,
            status = AnalysisRunStatus.COMPLETED,
            dataMode = dataMode,
            idempotencyKey = "run-1",
            forceRefresh = false,
            completedAt = NOW,
        )

    private fun evidence() =
        CollectedEvidence(
            id = 11L,
            runId = 3L,
            evidenceKey = "OFFICIAL_PRICE",
            valueJson = """{"amount":50000}""",
            source = "VWORLD_OFFICIAL_PRICE",
            sourceIdentifier = "getApartHousingPriceAttr",
            asOf = Instant.parse("2026-07-01T00:00:00Z"),
            collectedAt = NOW,
            confidence = 90,
            status = EvidenceStatus.AVAILABLE,
        )

    private fun assessment() =
        AnalysisRiskAssessment(
            score = null,
            grade = AnalysisRiskGrade.UNKNOWN,
            confidence = 35,
            summary = "규칙 요약",
            findings = listOf("가격 근거가 있습니다."),
            recommendations = listOf("가격을 확인하세요."),
            ruleVersion = "dive-2026-v1",
        )

    companion object {
        private val NOW = Instant.parse("2026-07-26T00:00:00Z")
    }
}

private class CountingReportClient : OpenAiReportClient {
    var calls = 0

    override fun generate(request: OpenAiReportRequest): AiReportResult {
        calls += 1
        return AiReportResult(
            summary = EvidenceBackedStatement("가격 근거를 확인하세요.", listOf("evidence-11")),
            residentialImpacts = emptyList(),
            actionGuide = listOf(EvidenceBackedStatement("가격을 확인하세요.", listOf("evidence-11"))),
        )
    }
}
