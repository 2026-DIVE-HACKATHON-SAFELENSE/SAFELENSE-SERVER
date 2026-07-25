// 비식별 근거만 OpenAI 해석 경계에 전달하고 검증 실패 시 규칙 문구로 대체하는 서비스
package com.safelense.analysis.interpretation

import com.safelense.analysis.AnalysisRiskAssessment
import com.safelense.analysis.evidence.CollectedEvidence
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.analysis.match.MatchedCase
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class AiEvidenceFact(
    val id: String,
    val evidenceKey: String,
    val valueJson: String?,
    val source: String,
    val asOf: Instant?,
    val status: EvidenceStatus,
)

data class OpenAiReportRequest(
    val facts: List<AiEvidenceFact>,
    val ruleResult: AnalysisRiskAssessment,
    val matchedCases: List<MatchedCase>,
)

fun interface OpenAiReportClient {
    fun generate(request: OpenAiReportRequest): AiReportResult
}

data class InterpretedReport(
    val result: AiReportResult,
    val fallback: Boolean,
    val model: String,
)

@Service
class OpenAiReportInterpreter(
    private val client: OpenAiReportClient,
    private val validator: ReportEvidenceValidator,
    private val properties: OpenAiProperties,
) {
    fun interpret(
        evidence: List<CollectedEvidence>,
        assessment: AnalysisRiskAssessment,
        matchedCases: List<MatchedCase>,
    ): InterpretedReport {
        val facts = evidence.map { item ->
            AiEvidenceFact(
                id = item.evidenceId(),
                evidenceKey = item.evidenceKey,
                valueJson = item.valueJson,
                source = item.source,
                asOf = item.asOf,
                status = item.status,
            )
        }
        val evidenceValues = facts.associate { it.id to it.valueJson } +
            matchedCases.associate {
                "case-${it.caseId}" to "${it.pattern} ${it.summary}"
            }
        return try {
            val result = client.generate(OpenAiReportRequest(facts, assessment, matchedCases))
            validator.validate(result, evidenceValues)
            InterpretedReport(result, false, properties.model)
        } catch (exception: Exception) {
            val reason = when (exception) {
                is InvalidAiEvidenceException -> exception.reason
                is OpenAiReportUnavailableException -> exception.reason
                else -> exception.javaClass.simpleName
            }
            logger.warn("OpenAI report fallback. reason={}", reason)
            val evidenceIds = facts
                .filter { it.status == EvidenceStatus.AVAILABLE }
                .map { it.id }
            InterpretedReport(
                result = AiReportResult(
                    summary = EvidenceBackedStatement(assessment.summary, evidenceIds),
                    residentialImpacts = emptyList(),
                    actionGuide = assessment.recommendations.map {
                        EvidenceBackedStatement(it, evidenceIds)
                    },
                ),
                fallback = true,
                model = properties.model,
            )
        }
    }

    private fun CollectedEvidence.evidenceId(): String = "evidence-${requireNotNull(id)}"

    companion object {
        private val logger = LoggerFactory.getLogger(OpenAiReportInterpreter::class.java)
    }
}
