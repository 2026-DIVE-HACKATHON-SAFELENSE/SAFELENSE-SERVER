// 단계별 분석 템플릿 HTTP 응답을 검증하는 MVC 테스트
package com.safelense.analysis

import com.safelense.auth.presentation.ApiExceptionHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AnalysisTemplateControllerTests {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AnalysisTemplateController(AnalysisTemplateCatalog()))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `returns the stage template`() {
        mockMvc.perform(get("/api/v1/analysis-templates/BEFORE_CONTRACT"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stage").value("BEFORE_CONTRACT"))
            .andExpect(jsonPath("$.version").value(ANALYSIS_TEMPLATE_VERSION))
            .andExpect(jsonPath("$.documents.length()").value(6))
            .andExpect(jsonPath("$.sections[0].sectionKey").value("FIELD_CHECK"))
    }

    @Test
    fun `rejects an unknown stage`() {
        mockMvc.perform(get("/api/v1/analysis-templates/UNKNOWN"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_STAGE"))
    }
}
