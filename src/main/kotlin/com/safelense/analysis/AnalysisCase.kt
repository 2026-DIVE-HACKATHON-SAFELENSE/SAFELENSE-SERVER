// 사용자와 주택에 귀속된 계약 단계별 분석 입력 케이스 엔티티
package com.safelense.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "analysis_cases")
class AnalysisCase(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "property_id", nullable = false)
    val propertyId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val stage: AnalysisStage,
    @Column(name = "template_version", nullable = false, length = 32)
    val templateVersion: String,
)
