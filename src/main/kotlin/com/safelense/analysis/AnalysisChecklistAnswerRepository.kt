// 분석 케이스의 체크리스트 답변 전체 교체를 지원하는 저장소
package com.safelense.analysis

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AnalysisChecklistAnswerRepository : JpaRepository<AnalysisChecklistAnswer, Long> {
    fun findAllByCaseId(caseId: Long): List<AnalysisChecklistAnswer>

    @Modifying
    @Query("delete from AnalysisChecklistAnswer answer where answer.caseId = :caseId")
    fun deleteAllByCaseId(@Param("caseId") caseId: Long): Int
}
