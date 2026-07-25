// 분석 실행별 불변 계약 의사결정 리포트 JSON 스냅샷을 저장하는 엔티티
package com.safelense.analysis.report

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "analysis_reports")
class AnalysisReport(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "run_id", nullable = false, unique = true) val runId: Long,
    @Column(name = "report_json", nullable = false, columnDefinition = "TEXT") val reportJson: String,
    @Column(name = "rule_version", nullable = false, length = 32) val ruleVersion: String,
    @Column(name = "prompt_version", length = 32) val promptVersion: String?,
    @Column(length = 100) val model: String?,
)
