// 사용자별 분석 이력 커서 조회와 소유 결과 상세 조회를 제공하는 저장소
package com.safelense.analysis

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AnalysisResultRepository : JpaRepository<AnalysisResult, Long> {
    @Query(
        """
        select analysisResult from AnalysisResult analysisResult
        where analysisResult.userId = :userId
          and (:cursor is null or analysisResult.id < :cursor)
          and (:stage is null or analysisResult.stage = :stage)
        order by analysisResult.id desc
        """,
    )
    fun findByUserIdWithCursor(
        @Param("userId") userId: Long,
        @Param("cursor") cursor: Long?,
        @Param("stage") stage: AnalysisStage?,
        pageable: Pageable,
    ): List<AnalysisResult>

    fun findByIdAndUserId(id: Long, userId: Long): AnalysisResult?

    fun findByCaseId(caseId: Long): AnalysisResult?

    fun existsByCaseId(caseId: Long): Boolean
}
