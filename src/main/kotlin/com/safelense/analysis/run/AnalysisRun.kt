// 계약 전 의사결정 분석 실행의 상태와 감사 정보를 저장하는 엔티티
package com.safelense.analysis.run

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class AnalysisRunStatus {
    QUEUED,
    COLLECTING,
    EXTRACTING_DOCUMENT,
    ANALYZING,
    COMPLETED,
    PARTIAL,
    FAILED,
}

enum class AnalysisDataMode {
    DEMO,
    LIVE,
}

@Entity
@Table(name = "analysis_runs")
class AnalysisRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "property_id", nullable = false)
    val propertyId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: AnalysisRunStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "data_mode", nullable = false, length = 16)
    val dataMode: AnalysisDataMode,
    @Column(name = "idempotency_key", nullable = false, length = 100)
    val idempotencyKey: String,
    @Column(name = "force_refresh", nullable = false)
    val forceRefresh: Boolean,
    @Column(name = "failure_code", length = 64)
    var failureCode: String? = null,
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
)
