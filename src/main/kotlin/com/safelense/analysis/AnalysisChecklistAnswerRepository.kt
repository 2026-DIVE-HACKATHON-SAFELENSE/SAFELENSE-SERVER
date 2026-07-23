// 분석 케이스의 체크리스트 답변 전체 교체를 지원하는 저장소
package com.safelense.analysis

import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisChecklistAnswerRepository : JpaRepository<AnalysisChecklistAnswer, Long> {
    fun findAllByCaseId(caseId: Long): List<AnalysisChecklistAnswer>
    fun deleteAllByCaseId(caseId: Long)
}
