// 분석 실행별 상담 검색 스냅샷을 순위대로 조회하는 저장소
package com.safelense.analysis.match

import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisCaseMatchRepository : JpaRepository<AnalysisCaseMatch, Long> {
    fun findAllByRunIdOrderByRankAsc(runId: Long): List<AnalysisCaseMatch>
}
