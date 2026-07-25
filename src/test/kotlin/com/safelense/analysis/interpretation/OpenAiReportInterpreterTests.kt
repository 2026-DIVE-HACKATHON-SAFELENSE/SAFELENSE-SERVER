// OpenAI 요청의 비식별 근거 구성과 장애 시 규칙 기반 대체를 검증하는 테스트
package com.safelense.analysis.interpretation

import com.safelense.analysis.AnalysisRiskGrade
import com.safelense.analysis.AnalysisRiskAssessment
import com.safelense.analysis.evidence.CollectedEvidence
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.analysis.match.MatchedCase
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class OpenAiReportInterpreterTests {
    private val properties = OpenAiProperties(
        apiKey = "test-key",
        model = "gpt-5.6",
        baseUrl = "https://api.openai.com/v1",
    )

    @Test
    fun `uses only normalized facts and returns validated model output`() {
        val client = RecordingOpenAiReportClient(
            result = AiReportResult(
                summary = EvidenceBackedStatement("가격 근거를 추가 확인하세요.", listOf("evidence-11")),
                residentialImpacts = emptyList(),
                actionGuide = listOf(EvidenceBackedStatement("공시가격을 대조하세요.", listOf("evidence-11"))),
            ),
        )
        val interpreter = OpenAiReportInterpreter(client, ReportEvidenceValidator(), properties)

        val interpreted = interpreter.interpret(listOf(evidence()), assessment(), cases())

        assertThat(interpreted.fallback).isFalse()
        assertThat(interpreted.model).isEqualTo("gpt-5.6")
        val fact = client.requests.single().facts.single()
        assertThat(fact.id).isEqualTo("evidence-11")
        assertThat(fact.evidenceKey).isEqualTo("OFFICIAL_PRICE")
        assertThat(fact.valueJson).contains("50000")
        assertThat(client.requests.single().toString())
            .doesNotContain("서울특별시", "임대인", "private/registry")
    }

    @Test
    fun `falls back to rule text when the OpenAI call fails`() {
        val client = OpenAiReportClient { throw IllegalStateException("unavailable") }
        val interpreter = OpenAiReportInterpreter(client, ReportEvidenceValidator(), properties)

        val interpreted = interpreter.interpret(listOf(evidence()), assessment(), cases())

        assertThat(interpreted.fallback).isTrue()
        assertThat(interpreted.result.summary.text).isEqualTo("규칙 요약")
        assertThat(interpreted.result.summary.evidenceIds).containsExactly("evidence-11")
    }

    @Test
    fun `accepts a statement cited by a matched consultation case`() {
        val client = RecordingOpenAiReportClient(
            result = AiReportResult(
                summary = EvidenceBackedStatement("유사 상담 사례를 확인하세요.", listOf("case-101")),
                residentialImpacts = emptyList(),
                actionGuide = emptyList(),
            ),
        )
        val interpreter = OpenAiReportInterpreter(client, ReportEvidenceValidator(), properties)
        val matched = MatchedCase(
            databaseId = 101L,
            caseId = "101",
            structuredScore = 0.8,
            semanticScore = 0.9,
            combinedScore = 0.845,
            pattern = "보증금반환 · 상담",
            summary = "아파트 보증금반환 유사 사례입니다.",
        )

        val interpreted = interpreter.interpret(listOf(evidence()), assessment(), listOf(matched))

        assertThat(interpreted.fallback).isFalse()
        assertThat(interpreted.result.summary.evidenceIds).containsExactly("case-101")
    }

    @Test
    fun `logs the validation reason without model output`(output: CapturedOutput) {
        val client = RecordingOpenAiReportClient(
            result = AiReportResult(
                summary = EvidenceBackedStatement("민감한 AI 원문", listOf("missing-evidence")),
            ),
        )
        val interpreter = OpenAiReportInterpreter(client, ReportEvidenceValidator(), properties)

        val interpreted = interpreter.interpret(listOf(evidence()), assessment(), cases())

        assertThat(interpreted.fallback).isTrue()
        assertThat(output.all)
            .contains("OpenAI report fallback", "reason=UNKNOWN_EVIDENCE_ID")
            .doesNotContain("민감한 AI 원문", "test-key")
    }

    private fun evidence() =
        CollectedEvidence(
            id = 11L,
            runId = 3L,
            evidenceKey = "OFFICIAL_PRICE",
            valueJson = """{"amount":50000}""",
            source = "VWORLD_OFFICIAL_PRICE",
            sourceIdentifier = "getApartHousingPriceAttr",
            asOf = Instant.parse("2026-07-01T00:00:00Z"),
            collectedAt = Instant.parse("2026-07-26T00:00:00Z"),
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
            recommendations = listOf("추가 확인하세요."),
            ruleVersion = "dive-2026-v1",
        )

    private fun cases() =
        listOf(MatchedCase("101", 0.82, "보증금반환 · 상담", "비식별 상담 패턴"))
}

private class RecordingOpenAiReportClient(
    private val result: AiReportResult,
) : OpenAiReportClient {
    val requests = mutableListOf<OpenAiReportRequest>()

    override fun generate(request: OpenAiReportRequest): AiReportResult {
        requests += request
        return result
    }
}
