// 정규화 근거에서 식별 정보 없는 상담 위험 패턴을 찾는 매처 경계
package com.safelense.analysis.match

import com.safelense.analysis.collection.CollectedEvidenceCommand

data class MatchedCase(
    val caseId: String,
    val similarity: Double,
    val pattern: String,
    val summary: String,
)

fun interface ConsultationCaseMatcher {
    fun match(evidence: List<CollectedEvidenceCommand>): List<MatchedCase>
}
