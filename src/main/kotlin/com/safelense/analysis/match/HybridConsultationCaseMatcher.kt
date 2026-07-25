// 구조화 조건과 임베딩 유사도를 결합해 실제 상담 사례를 검색하는 매처
package com.safelense.analysis.match

import com.safelense.analysis.AnalysisRiskAssessment
import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import kotlin.math.sqrt
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

data class ConsultationMatchRequest(
    val property: HomeProperty,
    val evidence: List<CollectedEvidenceCommand>,
    val assessment: AnalysisRiskAssessment,
)

data class ConsultationMatchResult(
    val cases: List<MatchedCase>,
    val degraded: Boolean,
)

@Component
class HybridConsultationCaseMatcher(
    private val repository: ConsultationCaseRepository,
    private val embeddingClient: EmbeddingClient,
    private val scorer: ConsultationStructuredScorer,
    private val objectMapper: ObjectMapper,
) : ConsultationCaseMatcher {
    override fun match(request: ConsultationMatchRequest): ConsultationMatchResult {
        val features = request.features()
        val candidates = repository.findAll()
            .map { case -> StructuredCandidate(case, scorer.score(features, case)) }
            .sortedWith(
                compareByDescending<StructuredCandidate>(StructuredCandidate::structuredScore)
                    .thenBy { it.case.externalCaseId },
            )
            .take(100)
        if (candidates.isEmpty()) {
            return ConsultationMatchResult(emptyList(), degraded = false)
        }
        val queryVector = try {
            embeddingClient.embed(listOf(request.embeddingInput())).single()
        } catch (_: Exception) {
            null
        }
        if (queryVector == null) {
            return ConsultationMatchResult(
                cases = candidates.map { it.toMatchedCase(null, it.structuredScore) }
                    .filter { it.combinedScore >= MINIMUM_SCORE }
                    .take(MAXIMUM_RESULTS),
                degraded = true,
            )
        }
        var degraded = false
        val matched = candidates.map { candidate ->
            val semantic = candidate.case.embeddingJson
                ?.let(::parseVector)
                ?.let { cosineSimilarity(queryVector, it) }
                ?.let { ((it + 1.0) / 2.0).coerceIn(0.0, 1.0) }
            if (semantic == null) {
                degraded = true
            }
            candidate.toMatchedCase(
                semanticScore = semantic,
                combinedScore = semantic?.let {
                    combineConsultationScores(candidate.structuredScore, it)
                } ?: candidate.structuredScore * 0.55,
            )
        }.filter { it.combinedScore >= MINIMUM_SCORE }
            .sortedWith(compareByDescending<MatchedCase>(MatchedCase::combinedScore).thenBy(MatchedCase::caseId))
            .take(MAXIMUM_RESULTS)
        return ConsultationMatchResult(matched, degraded)
    }

    private fun ConsultationMatchRequest.features(): ConsultationFeatures =
        ConsultationFeatures(
            depositBand = when {
                property.depositAmount < 10_000 -> "1억 미만"
                property.depositAmount < 20_000 -> "1억~2억"
                property.depositAmount < 30_000 -> "2억~3억"
                else -> "3억 이상"
            },
            housingTypes = when (property.buildingType) {
                BuildingType.APARTMENT -> setOf("아파트")
                BuildingType.OFFICETEL -> setOf("오피스텔")
                BuildingType.DETACHED_HOUSE -> setOf("다가구주택")
                BuildingType.MULTI_FAMILY -> setOf("다세대주택", "빌라", "원룸·도시형")
            },
            seniorRights = null,
            guaranteeStatus = null,
            province = evidence.firstOrNull {
                it.evidenceKey == "ADDRESS_RESOLUTION" &&
                    it.status == EvidenceStatus.AVAILABLE
            }?.valueJson
                ?.let(objectMapper::readTree)
                ?.get("province")
                ?.asString()
                ?.take(2),
        )

    private fun ConsultationMatchRequest.embeddingInput(): String {
        val features = features()
        return listOfNotNull(
            features.depositBand?.let { "보증금구간 $it" },
            features.housingTypes?.joinToString(",")?.let { "주택유형 $it" },
            features.province?.let { "지역 $it" },
            "위험등급 ${assessment.grade}",
            "규칙요약 ${assessment.summary}",
            assessment.findings.takeIf(List<String>::isNotEmpty)?.joinToString("\n", prefix = "위험요소 "),
            assessment.recommendations.takeIf(List<String>::isNotEmpty)
                ?.joinToString("\n", prefix = "확인사항 "),
        ).joinToString("\n")
    }

    private fun parseVector(json: String): List<Double>? =
        runCatching {
            objectMapper.readTree(json).values().map { it.asDouble() }.toList()
        }.getOrNull()?.takeIf(List<Double>::isNotEmpty)

    private fun cosineSimilarity(left: List<Double>, right: List<Double>): Double? {
        if (left.size != right.size || left.isEmpty()) {
            return null
        }
        val dot = left.indices.sumOf { left[it] * right[it] }
        val leftNorm = sqrt(left.sumOf { it * it })
        val rightNorm = sqrt(right.sumOf { it * it })
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return null
        }
        return dot / (leftNorm * rightNorm)
    }

    private fun StructuredCandidate.toMatchedCase(
        semanticScore: Double?,
        combinedScore: Double,
    ) = MatchedCase(
        databaseId = requireNotNull(case.id),
        caseId = case.externalCaseId,
        structuredScore = structuredScore,
        semanticScore = semanticScore,
        combinedScore = combinedScore,
        pattern = "${case.disputeType} · ${case.progressStage}",
        summary = "${case.housingType} 계약의 ${case.disputeType} 분쟁이 ${case.progressStage} 단계로 진행된 유사 사례입니다.",
    )

    private data class StructuredCandidate(
        val case: ConsultationCase,
        val structuredScore: Double,
    )

    companion object {
        private const val MINIMUM_SCORE = 0.45
        private const val MAXIMUM_RESULTS = 3
    }
}
