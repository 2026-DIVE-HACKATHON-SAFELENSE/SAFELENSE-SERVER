// 매물별 등기부 메타데이터를 소유 사용자 범위로 조회하는 저장소
package com.safelense.document

import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository

interface RegistryDocumentRepository : JpaRepository<RegistryDocument, Long> {
    fun findByIdAndPropertyId(id: Long, propertyId: Long): RegistryDocument?
    fun findAllByExpiresAtLessThanEqualAndDeletedAtIsNull(expiresAt: Instant): List<RegistryDocument>
}
