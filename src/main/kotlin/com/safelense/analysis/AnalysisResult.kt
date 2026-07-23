// 저장된 위험 분석 결과와 리포트 원본을 표현하는 엔티티
package com.safelense.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class AnalysisRiskGrade {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
}

@Entity
@Table(name = "analysis_results")
class AnalysisResult(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "case_id", nullable = false, unique = true)
    val caseId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "property_id", nullable = false)
    val propertyId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val stage: AnalysisStage,
    val score: Int?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val grade: AnalysisRiskGrade,
    @Column(nullable = false)
    val confidence: Int,
    @Column(nullable = false, length = 500)
    val summary: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val findings: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val recommendations: String,
    @Column(name = "rule_version", nullable = false, length = 32)
    val ruleVersion: String,
    @Column(name = "analyzed_at", nullable = false)
    val analyzedAt: Instant,
)
