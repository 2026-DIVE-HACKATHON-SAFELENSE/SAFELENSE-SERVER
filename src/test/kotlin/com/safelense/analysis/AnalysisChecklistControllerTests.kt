// 분석 체크리스트 전체 교체 HTTP 계약을 검증하는 MVC 테스트
package com.safelense.analysis

import com.safelense.auth.presentation.ApiExceptionHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AnalysisChecklistControllerTests {
    private val service = mock(AnalysisChecklistService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AnalysisChecklistController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `replaces partial checklist answers`() {
        val command = listOf(AnalysisChecklistAnswerCommand("VISITED_PROPERTY", true))
        `when`(service.replace(7L, 11L, command))
            .thenReturn(listOf(AnalysisChecklistAnswerView("VISITED_PROPERTY", true)))

        mockMvc.perform(
            put("/api/v1/analysis-cases/11/checklist")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"answers":[{"itemKey":"VISITED_PROPERTY","checked":true}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answers[0].itemKey").value("VISITED_PROPERTY"))
            .andExpect(jsonPath("$.answers[0].checked").value(true))
    }

    @Test
    fun `accepts an empty checklist`() {
        `when`(service.replace(7L, 11L, emptyList())).thenReturn(emptyList())

        mockMvc.perform(
            put("/api/v1/analysis-cases/11/checklist")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"answers":[]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answers").isEmpty())
    }

    @Test
    fun `maps invalid checklist errors`() {
        `when`(service.replace(7L, 11L, listOf(AnalysisChecklistAnswerCommand("UNKNOWN", true))))
            .thenThrow(InvalidAnalysisChecklistException())

        mockMvc.perform(
            put("/api/v1/analysis-cases/11/checklist")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"answers":[{"itemKey":"UNKNOWN","checked":true}]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CHECKLIST"))
    }

    @Test
    fun `rejects a blank item key before calling the service`() {
        mockMvc.perform(
            put("/api/v1/analysis-cases/11/checklist")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"answers":[{"itemKey":"","checked":true}]}"""),
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(service)
    }
}
