// 분석 실행의 멱등 조회와 워커용 비관적 잠금을 제공하는 저장소
package com.safelense.analysis.run

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AnalysisRunRepository : JpaRepository<AnalysisRun, Long> {
    fun findByPropertyIdAndIdempotencyKey(propertyId: Long, idempotencyKey: String): AnalysisRun?

    fun findByIdAndUserId(id: Long, userId: Long): AnalysisRun?

    fun findAllByPropertyIdAndUserIdOrderByIdDesc(propertyId: Long, userId: Long): List<AnalysisRun>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AnalysisRun run where run.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): AnalysisRun?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByStatusOrderByIdAsc(status: AnalysisRunStatus): AnalysisRun?
}
