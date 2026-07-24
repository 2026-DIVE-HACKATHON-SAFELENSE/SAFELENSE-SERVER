// 인증 사용자의 분석 이력 목록과 저장 결과 상세 조회를 처리하는 서비스
package com.safelense.analysis

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Schema(description = "분석 이력 목록에 표시하는 결과 요약")
data class AnalysisResultSummary(
    @field:Schema(description = "분석 결과 ID", example = "10")
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

@Schema(description = "커서 기반 분석 이력 페이지")
data class AnalysisHistoryPage(
    @field:Schema(description = "분석 결과 요약 목록")
    val analyses: List<AnalysisResultSummary>,
    @field:Schema(description = "다음 페이지 조회용 커서. 마지막 페이지면 null")
    val nextCursor: Long?,
    @field:Schema(description = "다음 페이지 존재 여부")
    val hasNext: Boolean,
)

@Schema(description = "위험 점수, 근거와 권고 사항을 포함한 분석 결과")
data class AnalysisResultDetail(
    @field:Schema(description = "분석 결과 ID", example = "10")
    val id: Long,
    @field:Schema(description = "분석을 실행한 케이스 ID", example = "42")
    val caseId: Long,
    val propertyId: Long,
    val stage: AnalysisStage,
    @field:Schema(description = "위험 점수. 근거가 부족하면 null")
    val score: Int?,
    @field:Schema(description = "위험 등급", example = "HIGH")
    val grade: AnalysisRiskGrade,
    @field:Schema(description = "입력 근거 충족률. 0부터 100 사이", example = "80")
    val confidence: Int,
    @field:Schema(description = "분석 결과 요약")
    val summary: String,
    @field:Schema(description = "위험 신호 근거 목록")
    val findings: List<String>,
    @field:Schema(description = "권고 행동 목록")
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
}

internal fun AnalysisResult.toDetail(): AnalysisResultDetail =
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
