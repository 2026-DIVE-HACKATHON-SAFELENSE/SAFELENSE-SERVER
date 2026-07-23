// 분석 이력 목록과 상세 조회의 HTTP 계약을 검증하는 테스트
package com.safelense.analysis

import com.safelense.auth.presentation.ApiExceptionHandler
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AnalysisResultControllerTests {
    private val service = mock(AnalysisResultService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AnalysisResultController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `lists analysis history with HTTP defaults`() {
        `when`(service.list(7L, null, 20, null)).thenReturn(
            AnalysisHistoryPage(
                analyses = listOf(summary()),
                nextCursor = 31L,
                hasNext = true,
            ),
        )

        mockMvc.perform(get("/api/v1/analyses").principal(authentication()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.analyses[0].id").value(31))
            .andExpect(jsonPath("$.analyses[0].stage").value("BEFORE_CONTRACT"))
            .andExpect(jsonPath("$.analyses[0].grade").value("MEDIUM"))
            .andExpect(jsonPath("$.nextCursor").value(31))
            .andExpect(jsonPath("$.hasNext").value(true))

        verify(service).list(7L, null, 20, null)
    }

    @Test
    fun `passes explicit cursor size and stage`() {
        `when`(service.list(7L, 50L, 5, AnalysisStage.AFTER_CONTRACT))
            .thenReturn(AnalysisHistoryPage(emptyList(), null, false))

        mockMvc.perform(
            get("/api/v1/analyses")
                .principal(authentication())
                .param("cursor", "50")
                .param("size", "5")
                .param("stage", "AFTER_CONTRACT"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.analyses").isEmpty)

        verify(service).list(7L, 50L, 5, AnalysisStage.AFTER_CONTRACT)
    }

    @Test
    fun `returns invalid request for malformed query and path parameters`() {
        listOf(
            "/api/v1/analyses?cursor=bad",
            "/api/v1/analyses?size=bad",
            "/api/v1/analyses?stage=bad",
            "/api/v1/analyses/bad",
        ).forEach { path ->
            mockMvc.perform(get(path).principal(authentication()))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `gets an owned analysis result`() {
        `when`(service.get(7L, 31L)).thenReturn(detail())

        mockMvc.perform(get("/api/v1/analyses/31").principal(authentication()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(31))
            .andExpect(jsonPath("$.findings[0]").value("위험 근거"))
            .andExpect(jsonPath("$.recommendations[0]").value("권고"))
            .andExpect(jsonPath("$.ruleVersion").value("2026-07-24-v1"))
    }

    @Test
    fun `hides an analysis result not owned by the user`() {
        `when`(service.get(7L, 31L)).thenThrow(AnalysisResultNotFoundException())

        mockMvc.perform(get("/api/v1/analyses/31").principal(authentication()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"))
    }

    private fun summary() =
        AnalysisResultSummary(
            id = 31L,
            caseId = 11L,
            propertyId = 5L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            score = 45,
            grade = AnalysisRiskGrade.MEDIUM,
            confidence = 70,
            summary = "확인이 필요한 위험 신호가 있습니다.",
            analyzedAt = Instant.parse("2026-07-24T10:15:30Z"),
        )

    private fun detail() =
        AnalysisResultDetail(
            id = 31L,
            caseId = 11L,
            propertyId = 5L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            score = 45,
            grade = AnalysisRiskGrade.MEDIUM,
            confidence = 70,
            summary = "확인이 필요한 위험 신호가 있습니다.",
            findings = listOf("위험 근거"),
            recommendations = listOf("권고"),
            ruleVersion = "2026-07-24-v1",
            analyzedAt = Instant.parse("2026-07-24T10:15:30Z"),
        )

    private fun authentication() = UsernamePasswordAuthenticationToken(7L, null)
}
