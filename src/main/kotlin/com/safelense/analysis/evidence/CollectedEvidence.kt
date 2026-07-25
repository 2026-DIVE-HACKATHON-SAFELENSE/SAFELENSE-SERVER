// 분석 실행에서 수집한 정규화 근거와 데이터 품질 상태를 저장하는 엔티티
package com.safelense.analysis.evidence

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Schema(description = "수집 근거의 가용성과 품질 상태")
enum class EvidenceStatus {
    AVAILABLE,
    NOT_AVAILABLE,
    UNAVAILABLE,
    STALE,
    CONFLICTING,
}

@Entity
@Table(name = "collected_evidence")
class CollectedEvidence(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "run_id", nullable = false)
    val runId: Long,
    @Column(name = "evidence_key", nullable = false, length = 64)
    val evidenceKey: String,
    @Column(name = "value_json", columnDefinition = "TEXT")
    val valueJson: String? = null,
    @Column(nullable = false, length = 64)
    val source: String,
    @Column(name = "source_identifier", length = 255)
    val sourceIdentifier: String? = null,
    @Column(name = "as_of")
    val asOf: Instant? = null,
    @Column(name = "collected_at", nullable = false)
    val collectedAt: Instant,
    @Column(nullable = false)
    val confidence: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val status: EvidenceStatus,
)
