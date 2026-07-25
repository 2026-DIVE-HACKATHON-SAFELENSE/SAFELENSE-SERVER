// 정규화 근거에서 식별 정보 없는 상담 위험 패턴을 찾는 매처 경계
package com.safelense.analysis.match

data class MatchedCase(
    val databaseId: Long,
    val caseId: String,
    val structuredScore: Double,
    val semanticScore: Double?,
    val combinedScore: Double,
    val pattern: String,
    val summary: String,
    val source: String = CONSULTATION_SOURCE,
) {
    constructor(
        caseId: String,
        similarity: Double,
        pattern: String,
        summary: String,
    ) : this(
        databaseId = 0,
        caseId = caseId,
        structuredScore = similarity,
        semanticScore = null,
        combinedScore = similarity,
        pattern = pattern,
        summary = summary,
        source = CONSULTATION_SOURCE,
    )

    val similarity: Double
        get() = combinedScore
}

fun interface ConsultationCaseMatcher {
    fun match(request: ConsultationMatchRequest): ConsultationMatchResult
}
