// 분석 서류 업로드·교체·삭제와 사용자별 접근 격리를 검증하는 서비스 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile

class AnalysisDocumentServiceTests {
    private val caseRepository = mock(AnalysisCaseRepository::class.java)
    private val documentRepository = mock(AnalysisDocumentRepository::class.java)
    private val service = AnalysisDocumentService(
        caseRepository,
        documentRepository,
        AnalysisTemplateCatalog(),
    )

    @Test
    fun `uploads a document into an empty slot`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(documentRepository.findByCaseIdAndDocumentType(11L, "REGISTRY_CERTIFICATE")).thenReturn(null)
        `when`(documentRepository.save(any(AnalysisDocument::class.java))).thenAnswer {
            (it.arguments[0] as AnalysisDocument).apply { id = 21L }
        }
        `when`(documentRepository.countByCaseId(11L)).thenReturn(1L)
        val file = MockMultipartFile("file", "registry.pdf", "application/pdf", "pdf".toByteArray())

        val result = service.upload(7L, 11L, "REGISTRY_CERTIFICATE", file)

        assertThat(result.document.id).isEqualTo(21L)
        assertThat(result.document.documentType).isEqualTo("REGISTRY_CERTIFICATE")
        assertThat(result.document.originalFileName).isEqualTo("registry.pdf")
        assertThat(result.document.mimeType).isEqualTo("application/pdf")
        assertThat(result.document.fileSize).isEqualTo(3L)
        assertThat(result.uploadedCount).isEqualTo(1)
        verify(caseRepository).findByIdAndUserIdForUpdate(11L, 7L)
    }

    @Test
    fun `replaces a document in the same slot and preserves its id`() {
        val existing = document()
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(documentRepository.findByCaseIdAndDocumentType(11L, "REGISTRY_CERTIFICATE")).thenReturn(existing)
        `when`(documentRepository.save(existing)).thenReturn(existing)
        `when`(documentRepository.countByCaseId(11L)).thenReturn(1L)
        val file = MockMultipartFile("file", "new.pdf", "application/pdf", "new".toByteArray())

        val result = service.upload(7L, 11L, "REGISTRY_CERTIFICATE", file)

        assertThat(result.document.id).isEqualTo(21L)
        assertThat(existing.originalFileName).isEqualTo("new.pdf")
        assertThat(existing.mimeType).isEqualTo("application/pdf")
        assertThat(existing.fileSize).isEqualTo(3L)
        assertThat(existing.content).containsExactly(110, 101, 119)
        assertThat(result.uploadedCount).isEqualTo(1)
        verify(documentRepository).save(existing)
    }

    @Test
    fun `accepts each supported MIME type`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(documentRepository.findByCaseIdAndDocumentType(11L, "REGISTRY_CERTIFICATE")).thenReturn(null)
        `when`(documentRepository.save(any(AnalysisDocument::class.java))).thenAnswer {
            (it.arguments[0] as AnalysisDocument).apply { id = 21L }
        }
        `when`(documentRepository.countByCaseId(11L)).thenReturn(1L)

        listOf("application/pdf", "image/jpeg", "image/png").forEach { mimeType ->
            val result = service.upload(
                7L,
                11L,
                "REGISTRY_CERTIFICATE",
                MockMultipartFile("file", "document", mimeType, byteArrayOf(1)),
            )

            assertThat(result.document.mimeType).isEqualTo(mimeType)
        }
    }

    @Test
    fun `accepts a document at the exact size limit`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(documentRepository.findByCaseIdAndDocumentType(11L, "REGISTRY_CERTIFICATE")).thenReturn(null)
        `when`(documentRepository.save(any(AnalysisDocument::class.java))).thenAnswer {
            (it.arguments[0] as AnalysisDocument).apply { id = 21L }
        }
        `when`(documentRepository.countByCaseId(11L)).thenReturn(1L)
        val file = MockMultipartFile(
            "file",
            "limit.pdf",
            "application/pdf",
            ByteArray(10 * 1024 * 1024),
        )

        val result = service.upload(7L, 11L, "REGISTRY_CERTIFICATE", file)

        assertThat(result.document.fileSize).isEqualTo(10L * 1024 * 1024)
    }

    @Test
    fun `rejects an unsupported slot empty file invalid name or unsupported MIME type`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())

        assertInvalid(
            "UNKNOWN",
            MockMultipartFile("file", "registry.pdf", "application/pdf", "pdf".toByteArray()),
        )
        assertInvalid(
            "REGISTRY_CERTIFICATE",
            MockMultipartFile("file", "empty.pdf", "application/pdf", byteArrayOf()),
        )
        assertInvalid(
            "REGISTRY_CERTIFICATE",
            MockMultipartFile("file", "   ", "application/pdf", "pdf".toByteArray()),
        )
        assertInvalid(
            "REGISTRY_CERTIFICATE",
            MockMultipartFile("file", "${"a".repeat(252)}.pdf", "application/pdf", "pdf".toByteArray()),
        )
        assertInvalid(
            "REGISTRY_CERTIFICATE",
            MockMultipartFile("file", "bad.txt", "text/plain", "bad".toByteArray()),
        )

        verifyNoInteractions(documentRepository)
    }

    @Test
    fun `rejects a document over the size limit`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())

        assertThatThrownBy {
            service.upload(
                7L,
                11L,
                "REGISTRY_CERTIFICATE",
                MockMultipartFile(
                    "file",
                    "big.pdf",
                    "application/pdf",
                    ByteArray(10 * 1024 * 1024 + 1),
                ),
            )
        }.isInstanceOf(AnalysisDocumentTooLargeException::class.java)

        verifyNoInteractions(documentRepository)
    }

    @Test
    fun `hides a case not owned by the user before uploading`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(null)

        assertThatThrownBy {
            service.upload(
                7L,
                11L,
                "REGISTRY_CERTIFICATE",
                MockMultipartFile("file", "registry.pdf", "application/pdf", "pdf".toByteArray()),
            )
        }.isInstanceOf(AnalysisCaseNotFoundException::class.java)

        verifyNoInteractions(documentRepository)
    }

    @Test
    fun `deletes a document from an owned case`() {
        val existing = document()
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(documentRepository.findByIdAndCaseId(21L, 11L)).thenReturn(existing)

        service.delete(7L, 11L, 21L)

        verify(caseRepository).findByIdAndUserIdForUpdate(11L, 7L)
        verify(documentRepository).delete(existing)
    }

    @Test
    fun `hides another users case before deleting`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(null)

        assertThatThrownBy { service.delete(7L, 11L, 21L) }
            .isInstanceOf(AnalysisCaseNotFoundException::class.java)

        verify(documentRepository, never()).findByIdAndCaseId(anyLong(), anyLong())
        verify(documentRepository, never()).delete(any(AnalysisDocument::class.java))
    }

    @Test
    fun `rejects a document belonging to another case`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(documentRepository.findByIdAndCaseId(21L, 11L)).thenReturn(null)

        assertThatThrownBy { service.delete(7L, 11L, 21L) }
            .isInstanceOf(AnalysisDocumentNotFoundException::class.java)

        verify(documentRepository, never()).delete(any(AnalysisDocument::class.java))
    }

    private fun assertInvalid(documentType: String, file: MockMultipartFile) {
        assertThatThrownBy {
            service.upload(7L, 11L, documentType, file)
        }.isInstanceOf(InvalidAnalysisDocumentException::class.java)
    }

    private fun analysisCase(): AnalysisCase =
        AnalysisCase(
            id = 11L,
            userId = 7L,
            propertyId = 3L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            templateVersion = ANALYSIS_TEMPLATE_VERSION,
        )

    private fun document(): AnalysisDocument =
        AnalysisDocument(
            id = 21L,
            caseId = 11L,
            documentType = "REGISTRY_CERTIFICATE",
            originalFileName = "registry.pdf",
            mimeType = "application/pdf",
            fileSize = 3L,
            content = "pdf".toByteArray(),
        )
}
