// 분석 케이스 생성과 사용자별 조회 HTTP 계약을 검증하는 MVC 테스트
package com.safelense.analysis

import com.safelense.auth.presentation.ApiExceptionHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AnalysisCaseControllerTests {
    private val service = mock(AnalysisCaseService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AnalysisCaseController(service, AnalysisTemplateCatalog()))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `creates an analysis case`() {
        val created = AnalysisCaseCreated(11L, 3L, AnalysisStage.BEFORE_CONTRACT, ANALYSIS_TEMPLATE_VERSION)
        `when`(service.create(7L, AnalysisCaseCreateCommand(AnalysisStage.BEFORE_CONTRACT, 3L)))
            .thenReturn(created)

        mockMvc.perform(
            post("/api/v1/analysis-cases")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stage":"BEFORE_CONTRACT","propertyId":3}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(11))
            .andExpect(jsonPath("$.stage").value("BEFORE_CONTRACT"))
    }

    @Test
    fun `gets an owned analysis case`() {
        `when`(service.get(7L, 11L)).thenReturn(caseView())

        mockMvc.perform(
            get("/api/v1/analysis-cases/11")
                .principal(UsernamePasswordAuthenticationToken(7L, null)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.documents.length()").value(6))
            .andExpect(jsonPath("$.uploadedCount").value(0))
    }

    @Test
    fun `rejects an invalid analysis stage`() {
        mockMvc.perform(
            post("/api/v1/analysis-cases")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stage":"UNKNOWN","propertyId":3}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_STAGE"))
    }

    @Test
    fun `hides an analysis case owned by another user`() {
        `when`(service.get(7L, 11L)).thenThrow(AnalysisCaseNotFoundException())

        mockMvc.perform(
            get("/api/v1/analysis-cases/11")
                .principal(UsernamePasswordAuthenticationToken(7L, null)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ANALYSIS_CASE_NOT_FOUND"))
    }

    private fun caseView(): AnalysisCaseView {
        val template = AnalysisTemplateCatalog().get(AnalysisStage.BEFORE_CONTRACT)
        return AnalysisCaseView(
            id = 11L,
            propertyId = 3L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            templateVersion = ANALYSIS_TEMPLATE_VERSION,
            documents = template.documents.map {
                AnalysisDocumentSlotView(
                    documentType = it.documentType,
                    label = it.label,
                    required = it.required,
                    documentId = null,
                    originalFileName = null,
                    mimeType = null,
                    fileSize = null,
                )
            },
            uploadedCount = 0,
            answers = emptyList(),
        )
    }
}
