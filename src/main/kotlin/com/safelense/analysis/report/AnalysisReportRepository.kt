// 분석 실행 ID로 불변 리포트 스냅샷을 조회하는 저장소
package com.safelense.analysis.report

import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisReportRepository : JpaRepository<AnalysisReport, Long> {
    fun findByRunId(runId: Long): AnalysisReport?
}
