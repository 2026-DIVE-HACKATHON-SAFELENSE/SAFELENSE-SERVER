// 실제 상담 사례의 조회와 멱등 적재를 제공하는 저장소
package com.safelense.analysis.match

import org.springframework.data.jpa.repository.JpaRepository

interface ConsultationCaseRepository : JpaRepository<ConsultationCase, Long> {
    fun findBySourceAndExternalCaseId(source: String, externalCaseId: String): ConsultationCase?
}
