// AI 문장의 근거 ID와 근거 없는 숫자·법률 단정을 검증하는 보호 장치
package com.safelense.analysis.interpretation

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Component

class InvalidAiEvidenceException(val reason: String) : RuntimeException(reason)

@Schema(description = "수집 근거를 인용하는 리포트 문장")
data class EvidenceBackedStatement(
    @field:Schema(description = "사용자에게 표시할 해석 또는 행동 문장")
    var text: String = "",
    @field:Schema(description = "문장을 뒷받침하는 수집 근거 ID 목록", example = "[\"evidence-101\"]")
    var evidenceIds: List<String> = emptyList(),
)

enum class AiAttentionLevel {
    SAFE,
    CAUTION,
    DANGER,
    UNKNOWN,
}

enum class AiMitigationStatus {
    POSSIBLE,
    DIFFICULT,
    UNKNOWN,
}

data class AiReportResult(
    var summary: EvidenceBackedStatement = EvidenceBackedStatement(),
    var attentionLevel: AiAttentionLevel = AiAttentionLevel.UNKNOWN,
    var mitigationStatus: AiMitigationStatus = AiMitigationStatus.UNKNOWN,
    var residentialImpacts: List<EvidenceBackedStatement> = emptyList(),
    var actionGuide: List<EvidenceBackedStatement> = emptyList(),
) {
    fun statements(): List<EvidenceBackedStatement> = listOf(summary) + residentialImpacts + actionGuide
}

@Component
class ReportEvidenceValidator {
    fun validate(result: AiReportResult, allowedIds: Set<String>) {
        result.statements().forEach { statement ->
            if (statement.text.isBlank()) {
                throw InvalidAiEvidenceException("EMPTY_TEXT")
            }
            if (statement.evidenceIds.isEmpty()) {
                throw InvalidAiEvidenceException("MISSING_EVIDENCE_IDS")
            }
            if (statement.evidenceIds.any { it !in allowedIds }) {
                throw InvalidAiEvidenceException("UNKNOWN_EVIDENCE_ID")
            }
        }
    }

    fun validate(result: AiReportResult, evidenceValues: Map<String, String?>) {
        validate(result, evidenceValues.keys)
        result.statements().forEach { statement ->
            if (LEGAL_JUDGMENT_TERMS.any(statement.text::contains)) {
                throw InvalidAiEvidenceException("LEGAL_JUDGMENT")
            }
            if (
                statement.evidenceIds.any { it.startsWith("case-") } &&
                CASE_CONCLUSION_TERMS.any(statement.text::contains)
            ) {
                throw InvalidAiEvidenceException("CASE_CONCLUSION")
            }
            val citedValues = statement.evidenceIds
                .mapNotNull(evidenceValues::get)
                .joinToString(" ")
            NUMBER.findAll(statement.text).forEach { match ->
                if (!citedValues.contains(match.value)) {
                    throw InvalidAiEvidenceException("UNSUPPORTED_NUMBER")
                }
            }
        }
    }

    companion object {
        private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
        private val LEGAL_JUDGMENT_TERMS = listOf("법적으로 안전", "법적으로 보장", "불법이 확실", "위법이 확실")
        private val CASE_CONCLUSION_TERMS = listOf(
            "사고 확률",
            "계약이 안전",
            "계약은 안전",
            "계약이 위험",
            "계약은 위험",
            "반환이 보장",
        )
    }
}
