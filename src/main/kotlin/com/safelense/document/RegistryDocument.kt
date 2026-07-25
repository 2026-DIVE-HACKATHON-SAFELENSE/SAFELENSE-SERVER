// 매물에 연결된 등기부 원본의 객체 저장소 메타데이터를 저장하는 엔티티
package com.safelense.document

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Schema(description = "등기 문서 추출 및 만료 상태")
enum class RegistryExtractionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    EXPIRED,
}

@Entity
@Table(name = "registry_documents")
class RegistryDocument(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "property_id", nullable = false) val propertyId: Long,
    @Column(name = "storage_key", nullable = false, length = 500) val storageKey: String,
    @Column(nullable = false, length = 64) val sha256: String,
    @Column(name = "mime_type", nullable = false, length = 100) val mimeType: String,
    @Column(name = "file_size", nullable = false) val fileSize: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 32) var extractionStatus: RegistryExtractionStatus,
    @Column(name = "expires_at", nullable = false) val expiresAt: Instant,
    @Column(name = "deleted_at") var deletedAt: Instant? = null,
)
