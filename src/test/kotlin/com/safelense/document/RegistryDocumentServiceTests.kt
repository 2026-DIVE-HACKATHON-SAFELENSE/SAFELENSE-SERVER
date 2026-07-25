// 등기부 원본의 업로드·삭제·만료 처리와 객체 키 비공개 계약을 검증하는 테스트
package com.safelense.document

import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import com.safelense.property.HomePropertyRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile

class RegistryDocumentServiceTests {
    private val propertyRepository = mock(HomePropertyRepository::class.java)
    private val documentRepository = mock(RegistryDocumentRepository::class.java)
    private val storage = RecordingRegistryDocumentStorage()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val properties = RegistryDocumentProperties(
        bucket = "registry-bucket",
        kmsKeyId = "kms-key",
        retention = Duration.ofDays(30),
    )
    private val service = RegistryDocumentService(
        propertyRepository,
        documentRepository,
        storage,
        properties,
        clock,
    )

    @Test
    fun `uploads a PDF and persists only its private object reference`() {
        val file = MockMultipartFile("file", "registry.pdf", "application/pdf", "pdf".toByteArray())
        val expiresAt = NOW.plus(Duration.ofDays(30))
        `when`(propertyRepository.findByIdAndUserId(2L, 1L)).thenReturn(property())
        `when`(documentRepository.saveAndFlush(any(RegistryDocument::class.java))).thenAnswer {
            (it.arguments[0] as RegistryDocument).apply { id = 3L }
        }

        val result = service.upload(1L, 2L, file)

        val captor = ArgumentCaptor.forClass(RegistryDocument::class.java)
        verify(documentRepository).saveAndFlush(captor.capture())
        assertThat(captor.value.storageKey).isEqualTo("private/1/a.pdf")
        assertThat(captor.value.sha256).hasSize(64)
        assertThat(storage.putCalls.single().content).containsExactly(112, 100, 102)
        assertThat(storage.putCalls.single().contentType).isEqualTo("application/pdf")
        assertThat(storage.putCalls.single().expiresAt).isEqualTo(expiresAt)
        assertThat(result.id).isEqualTo(3L)
        assertThat(result.mimeType).isEqualTo("application/pdf")
        assertThat(result.expiresAt).isEqualTo(expiresAt)
    }

    @Test
    fun `deletes the private object and marks its metadata deleted`() {
        val document = document()
        `when`(propertyRepository.findByIdAndUserId(2L, 1L)).thenReturn(property())
        `when`(documentRepository.findByIdAndPropertyId(3L, 2L)).thenReturn(document)

        service.delete(1L, 2L, 3L)

        assertThat(storage.deletedKeys).containsExactly("private/1/a.pdf")
        assertThat(document.deletedAt).isEqualTo(NOW)
    }

    @Test
    fun `expires overdue objects and records the expired extraction status`() {
        val document = document()
        `when`(documentRepository.findAllByExpiresAtLessThanEqualAndDeletedAtIsNull(NOW))
            .thenReturn(listOf(document))

        val expiredCount = service.expireDue(NOW)

        assertThat(storage.deletedKeys).containsExactly("private/1/a.pdf")
        assertThat(document.extractionStatus).isEqualTo(RegistryExtractionStatus.EXPIRED)
        assertThat(document.deletedAt).isEqualTo(NOW)
        assertThat(expiredCount).isEqualTo(1)
    }

    private fun property() =
        HomeProperty(
            id = 2L,
            userId = 1L,
            address = "서울시 중구 1",
            depositAmount = 20000,
            buildingType = BuildingType.APARTMENT,
        )

    private fun document() =
        RegistryDocument(
            id = 3L,
            propertyId = 2L,
            storageKey = "private/1/a.pdf",
            sha256 = "a".repeat(64),
            mimeType = "application/pdf",
            fileSize = 3,
            extractionStatus = RegistryExtractionStatus.PENDING,
            expiresAt = NOW.plus(Duration.ofDays(30)),
        )

    companion object {
        private val NOW = Instant.parse("2026-07-26T00:00:00Z")
    }
}

private class RecordingRegistryDocumentStorage : RegistryDocumentStorage {
    data class PutCall(
        val content: ByteArray,
        val contentType: String,
        val sha256: String,
        val expiresAt: Instant,
    )

    val putCalls = mutableListOf<PutCall>()
    val deletedKeys = mutableListOf<String>()

    override fun put(
        content: ByteArray,
        contentType: String,
        sha256: String,
        expiresAt: Instant,
    ): StoredRegistryDocument {
        putCalls += PutCall(content, contentType, sha256, expiresAt)
        return StoredRegistryDocument("private/1/a.pdf", expiresAt)
    }

    override fun delete(storageKey: String) {
        deletedKeys += storageKey
    }
}
