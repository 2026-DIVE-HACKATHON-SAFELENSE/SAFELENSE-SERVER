// 인증 사용자의 분석 이력 목록과 저장 결과 상세 조회를 처리하는 서비스
package com.safelense.analysis

import java.time.Instant
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class AnalysisResultSummary(
    val id: Long,
    val caseId: Long,
    val propertyId: Long,
    val stage: AnalysisStage,
    val score: Int?,
    val grade: AnalysisRiskGrade,
    val confidence: Int,
    val summary: String,
    val analyzedAt: Instant,
)

data class AnalysisHistoryPage(
    val analyses: List<AnalysisResultSummary>,
    val nextCursor: Long?,
    val hasNext: Boolean,
)

data class AnalysisResultDetail(
    val id: Long,
    val caseId: Long,
    val propertyId: Long,
    val stage: AnalysisStage,
    val score: Int?,
    val grade: AnalysisRiskGrade,
    val confidence: Int,
    val summary: String,
    val findings: List<String>,
    val recommendations: List<String>,
    val ruleVersion: String,
    val analyzedAt: Instant,
)

@Service
class AnalysisResultService(
    private val repository: AnalysisResultRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        userId: Long,
        cursor: Long?,
        size: Int,
        stage: AnalysisStage?,
    ): AnalysisHistoryPage {
        if ((cursor != null && cursor <= 0) || size !in 1..100) {
            throw InvalidAnalysisResultRequestException()
        }

        val results = repository.findByUserIdWithCursor(
            userId,
            cursor,
            stage,
            PageRequest.of(0, size + 1),
        )
        val hasNext = results.size > size
        val analyses = results.take(size).map { it.toSummary() }
        return AnalysisHistoryPage(
            analyses = analyses,
            nextCursor = if (hasNext) analyses.last().id else null,
            hasNext = hasNext,
        )
    }

    @Transactional(readOnly = true)
    fun get(userId: Long, analysisId: Long): AnalysisResultDetail =
        repository.findByIdAndUserId(analysisId, userId)?.toDetail()
            ?: throw AnalysisResultNotFoundException()

    private fun AnalysisResult.toSummary(): AnalysisResultSummary =
        AnalysisResultSummary(
            id = requireNotNull(id),
            caseId = caseId,
            propertyId = propertyId,
            stage = stage,
            score = score,
            grade = grade,
            confidence = confidence,
            summary = summary,
            analyzedAt = analyzedAt,
        )

    private fun AnalysisResult.toDetail(): AnalysisResultDetail =
        AnalysisResultDetail(
            id = requireNotNull(id),
            caseId = caseId,
            propertyId = propertyId,
            stage = stage,
            score = score,
            grade = grade,
            confidence = confidence,
            summary = summary,
            findings = findings.toItems(),
            recommendations = recommendations.toItems(),
            ruleVersion = ruleVersion,
            analyzedAt = analyzedAt,
        )

    private fun String.toItems(): List<String> =
        lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
}
