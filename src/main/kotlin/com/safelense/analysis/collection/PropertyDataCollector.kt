// 후보 매물에서 실행별 정규화 근거 명령을 만드는 수집기 경계
package com.safelense.analysis.collection

import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.property.HomeProperty
import java.time.Instant

data class CollectedEvidenceCommand(
    val evidenceKey: String,
    val valueJson: String?,
    val source: String,
    val sourceIdentifier: String?,
    val asOf: Instant?,
    val collectedAt: Instant,
    val confidence: Int,
    val status: EvidenceStatus,
)

fun interface PropertyDataCollector {
    fun collect(property: HomeProperty): List<CollectedEvidenceCommand>
}
