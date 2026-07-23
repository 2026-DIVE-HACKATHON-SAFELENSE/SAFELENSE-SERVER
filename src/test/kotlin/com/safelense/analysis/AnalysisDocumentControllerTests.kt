// 분석 서류 업로드·삭제 HTTP 계약과 오류 응답을 검증하는 MVC 테스트
package com.safelense.analysis

import com.safelense.auth.presentation.ApiExceptionHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.multipart.MaxUploadSizeExceededException

class AnalysisDocumentControllerTests {
    private val service = mock(AnalysisDocumentService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AnalysisDocumentController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `uploads a document for the authenticated user`() {
        val file = pdfFile()
        `when`(service.upload(7L, 11L, "REGISTRY_CERTIFICATE", file))
            .thenReturn(uploadResult())

        mockMvc.perform(
            multipart("/api/v1/analysis-cases/11/documents")
                .file(file)
                .param("documentType", "REGISTRY_CERTIFICATE")
                .principal(authentication()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.document.id").value(21))
            .andExpect(jsonPath("$.document.documentType").value("REGISTRY_CERTIFICATE"))
            .andExpect(jsonPath("$.document.originalFileName").value("registry.pdf"))
            .andExpect(jsonPath("$.document.mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.document.fileSize").value(3))
            .andExpect(jsonPath("$.uploadedCount").value(1))

        verify(service).upload(7L, 11L, "REGISTRY_CERTIFICATE", file)
    }

    @Test
    fun `deletes a document for the authenticated user`() {
        mockMvc.perform(
            delete("/api/v1/analysis-cases/11/documents/21")
                .principal(authentication()),
        )
            .andExpect(status().isNoContent)

        verify(service).delete(7L, 11L, 21L)
    }

    @Test
    fun `returns bad request for an invalid document`() {
        performUploadWithFailure(InvalidAnalysisDocumentException())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"))
            .andExpect(jsonPath("$.message").value("Document is invalid."))
    }

    @Test
    fun `returns payload too large for an oversized document`() {
        performUploadWithFailure(AnalysisDocumentTooLargeException())
            .andExpect(status().`is`(413))
            .andExpect(jsonPath("$.code").value("DOCUMENT_TOO_LARGE"))
            .andExpect(jsonPath("$.message").value("Document is too large."))
    }

    @Test
    fun `returns payload too large when multipart parsing rejects the request`() {
        performUploadWithFailure(MaxUploadSizeExceededException(10L * 1024 * 1024))
            .andExpect(status().`is`(413))
            .andExpect(jsonPath("$.code").value("DOCUMENT_TOO_LARGE"))
    }

    @Test
    fun `hides an analysis case not owned by the user during upload`() {
        performUploadWithFailure(AnalysisCaseNotFoundException())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ANALYSIS_CASE_NOT_FOUND"))
    }

    @Test
    fun `returns not found for a document outside the case`() {
        doThrow(AnalysisDocumentNotFoundException()).`when`(service).delete(7L, 11L, 21L)

        mockMvc.perform(
            delete("/api/v1/analysis-cases/11/documents/21")
                .principal(authentication()),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ANALYSIS_DOCUMENT_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Analysis document was not found."))
    }

    @Test
    fun `returns case not found when deleting from a missing or non-owned case`() {
        doThrow(AnalysisCaseNotFoundException()).`when`(service).delete(7L, 11L, 21L)

        mockMvc.perform(
            delete("/api/v1/analysis-cases/11/documents/21")
                .principal(authentication()),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ANALYSIS_CASE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Analysis case was not found."))
    }

    private fun performUploadWithFailure(exception: RuntimeException): ResultActions {
        val file = pdfFile()
        doThrow(exception).`when`(service).upload(7L, 11L, "REGISTRY_CERTIFICATE", file)
        return mockMvc.perform(
            multipart("/api/v1/analysis-cases/11/documents")
                .file(file)
                .param("documentType", "REGISTRY_CERTIFICATE")
                .principal(authentication()),
        )
    }

    private fun authentication() = UsernamePasswordAuthenticationToken(7L, null)

    private fun pdfFile() =
        MockMultipartFile("file", "registry.pdf", "application/pdf", "pdf".toByteArray())

    private fun uploadResult() =
        AnalysisDocumentUploadResult(
            document = AnalysisDocumentView(
                id = 21L,
                documentType = "REGISTRY_CERTIFICATE",
                originalFileName = "registry.pdf",
                mimeType = "application/pdf",
                fileSize = 3L,
            ),
            uploadedCount = 1,
        )
}
