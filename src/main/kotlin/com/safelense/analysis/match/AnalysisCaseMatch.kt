// 분석 실행에서 선택한 상담 사례와 당시 유사도 점수를 저장하는 엔티티
package com.safelense.analysis.match

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "analysis_case_matches")
class AnalysisCaseMatch(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "run_id", nullable = false)
    val runId: Long,
    @Column(name = "consultation_case_id", nullable = false)
    val consultationCaseId: Long,
    @Column(nullable = false)
    val rank: Int,
    @Column(name = "structured_score", nullable = false)
    val structuredScore: Double,
    @Column(name = "semantic_score")
    val semanticScore: Double?,
    @Column(name = "combined_score", nullable = false)
    val combinedScore: Double,
    @Column(nullable = false, length = 64)
    val source: String,
    @Column(nullable = false, length = 255)
    val pattern: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val summary: String,
)
