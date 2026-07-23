// 사용자 소유 분석 케이스 조회와 입력 변경 잠금을 제공하는 저장소
package com.safelense.analysis

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AnalysisCaseRepository : JpaRepository<AnalysisCase, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): AnalysisCase?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select analysisCase from AnalysisCase analysisCase where analysisCase.id = :id and analysisCase.userId = :userId")
    fun findByIdAndUserIdForUpdate(
        @Param("id") id: Long,
        @Param("userId") userId: Long,
    ): AnalysisCase?
}
