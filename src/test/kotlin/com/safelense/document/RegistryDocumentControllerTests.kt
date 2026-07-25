// 후보 매물 등기부 업로드·삭제 HTTP 계약과 객체 키 비공개를 검증하는 테스트
package com.safelense.document

import com.safelense.auth.presentation.ApiExceptionHandler
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class RegistryDocumentControllerTests {
    private val service = mock(RegistryDocumentService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(RegistryDocumentController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `uploads a registry PDF without exposing its private object key`() {
        val file = MockMultipartFile("file", "registry.pdf", "application/pdf", "pdf".toByteArray())
        `when`(service.upload(1L, 2L, file)).thenReturn(
            RegistryDocumentView(
                id = 3L,
                mimeType = "application/pdf",
                fileSize = 3,
                extractionStatus = RegistryExtractionStatus.PENDING,
                expiresAt = Instant.parse("2026-08-25T00:00:00Z"),
            ),
        )

        mockMvc.perform(
            multipart("/api/v1/properties/2/registry-documents")
                .file(file)
                .principal(authentication()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.document.id").value(3))
            .andExpect(jsonPath("$.document.extractionStatus").value("PENDING"))
            .andExpect(jsonPath("$.document.storageKey").doesNotExist())
            .andExpect(jsonPath("$.document.objectKey").doesNotExist())

        verify(service).upload(1L, 2L, file)
    }

    @Test
    fun `deletes a registry document for an owned property`() {
        mockMvc.perform(
            delete("/api/v1/properties/2/registry-documents/3")
                .principal(authentication()),
        )
            .andExpect(status().isNoContent)

        verify(service).delete(1L, 2L, 3L)
    }

    @Test
    fun `returns a terminal document expired error`() {
        doThrow(RegistryDocumentExpiredException())
            .`when`(service)
            .delete(1L, 2L, 3L)

        mockMvc.perform(
            delete("/api/v1/properties/2/registry-documents/3")
                .principal(authentication()),
        )
            .andExpect(status().isGone)
            .andExpect(jsonPath("$.code").value("DOCUMENT_EXPIRED"))
            .andExpect(jsonPath("$.retryable").value(false))
    }

    private fun authentication() = UsernamePasswordAuthenticationToken(1L, null)
}
