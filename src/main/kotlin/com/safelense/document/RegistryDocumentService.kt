// 인증 사용자의 등기부 원본 업로드·삭제·만료 처리를 조정하는 서비스
package com.safelense.document

import com.safelense.property.HomePropertyNotFoundException
import com.safelense.property.HomePropertyRepository
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.HexFormat
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private const val MAX_REGISTRY_DOCUMENT_SIZE = 10L * 1024 * 1024
private const val REGISTRY_DOCUMENT_MIME_TYPE = "application/pdf"

class InvalidRegistryDocumentException : RuntimeException()

class RegistryDocumentTooLargeException : RuntimeException()

class RegistryDocumentNotFoundException : RuntimeException()

class RegistryDocumentExpiredException : RuntimeException()

data class RegistryDocumentView(
    val id: Long,
    val mimeType: String,
    val fileSize: Long,
    val extractionStatus: RegistryExtractionStatus,
    val expiresAt: Instant,
)

@Service
class RegistryDocumentService(
    private val propertyRepository: HomePropertyRepository,
    private val documentRepository: RegistryDocumentRepository,
    private val storage: RegistryDocumentStorage,
    private val properties: RegistryDocumentProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun upload(userId: Long, propertyId: Long, file: MultipartFile): RegistryDocumentView {
        propertyRepository.findByIdAndUserId(propertyId, userId) ?: throw HomePropertyNotFoundException()
        if (file.isEmpty || file.contentType != REGISTRY_DOCUMENT_MIME_TYPE) {
            throw InvalidRegistryDocumentException()
        }
        if (file.size > MAX_REGISTRY_DOCUMENT_SIZE) {
            throw RegistryDocumentTooLargeException()
        }

        val content = file.bytes
        val sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))
        val expiresAt = Instant.now(clock).plus(properties.retention)
        val stored = storage.put(content, REGISTRY_DOCUMENT_MIME_TYPE, sha256, expiresAt)
        val document = documentRepository.saveAndFlush(
            RegistryDocument(
                propertyId = propertyId,
                storageKey = stored.storageKey,
                sha256 = sha256,
                mimeType = REGISTRY_DOCUMENT_MIME_TYPE,
                fileSize = content.size.toLong(),
                extractionStatus = RegistryExtractionStatus.PENDING,
                expiresAt = stored.expiresAt,
            ),
        )
        return document.toView()
    }

    @Transactional
    fun delete(userId: Long, propertyId: Long, documentId: Long) {
        propertyRepository.findByIdAndUserId(propertyId, userId) ?: throw HomePropertyNotFoundException()
        val document = documentRepository.findByIdAndPropertyId(documentId, propertyId)
            ?: throw RegistryDocumentNotFoundException()
        if (document.extractionStatus == RegistryExtractionStatus.EXPIRED) {
            throw RegistryDocumentExpiredException()
        }
        if (document.deletedAt != null) {
            throw RegistryDocumentNotFoundException()
        }

        storage.delete(document.storageKey)
        document.deletedAt = Instant.now(clock)
    }

    @Transactional
    fun expireDue(now: Instant = Instant.now(clock)): Int {
        val documents = documentRepository.findAllByExpiresAtLessThanEqualAndDeletedAtIsNull(now)
        documents.forEach { document ->
            storage.delete(document.storageKey)
            document.extractionStatus = RegistryExtractionStatus.EXPIRED
            document.deletedAt = now
        }
        return documents.size
    }

    private fun RegistryDocument.toView() =
        RegistryDocumentView(
            id = requireNotNull(id),
            mimeType = mimeType,
            fileSize = fileSize,
            extractionStatus = extractionStatus,
            expiresAt = expiresAt,
        )
}
