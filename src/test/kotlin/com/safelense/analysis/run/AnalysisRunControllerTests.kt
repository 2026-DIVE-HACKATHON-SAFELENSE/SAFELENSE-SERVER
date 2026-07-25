// 계약 전 분석 실행 생성과 상태 조회 HTTP 계약을 검증하는 테스트
package com.safelense.analysis.run

import com.safelense.auth.presentation.ApiExceptionHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AnalysisRunControllerTests {
    private val service = mock(AnalysisRunService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AnalysisRunController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `queues an analysis with an idempotency key and force refresh flag`() {
        `when`(service.create(1L, 2L, "run-1", true)).thenReturn(view())

        mockMvc.perform(
            post("/api/v1/properties/2/analyses")
                .principal(authentication())
                .header("Idempotency-Key", "run-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"forceRefresh":true}"""),
        )
            .andExpect(status().isAccepted)
            .andExpect(header().string("Location", "/api/v1/analyses/3/status"))
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.dataMode").value("DEMO"))

        verify(service).create(1L, 2L, "run-1", true)
    }

    @Test
    fun `returns the owned analysis status`() {
        `when`(service.status(1L, 3L)).thenReturn(view())

        mockMvc.perform(
            get("/api/v1/analyses/3/status")
                .principal(authentication()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.status").value("QUEUED"))

        verify(service).status(1L, 3L)
    }

    @Test
    fun `returns analysis history for an owned property`() {
        `when`(service.history(1L, 2L)).thenReturn(AnalysisRunHistoryView(listOf(view())))

        mockMvc.perform(
            get("/api/v1/properties/2/analyses")
                .principal(authentication()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.analyses[0].id").value(3))
            .andExpect(jsonPath("$.analyses[0].retryable").value(false))

        verify(service).history(1L, 2L)
    }

    private fun view() =
        AnalysisRunView(
            id = 3L,
            propertyId = 2L,
            status = AnalysisRunStatus.QUEUED,
            dataMode = AnalysisDataMode.DEMO,
            forceRefresh = true,
            failureCode = null,
            retryable = false,
        )

    private fun authentication() = UsernamePasswordAuthenticationToken(1L, null)
}
