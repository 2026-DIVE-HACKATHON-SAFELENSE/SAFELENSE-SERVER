// 위험 분석 실행 API의 요청 검증·상태 코드·오류 응답을 검증하는 테스트
package com.safelense.analysis

import com.safelense.auth.presentation.ApiExceptionHandler
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AnalysisExecutionControllerTests {
    private val service = mock(AnalysisExecutionService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AnalysisExecutionController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `creates an analysis result and returns its location`() {
        val command = completeCommand()
        `when`(service.analyze(7L, 11L, "request-1", command))
            .thenReturn(AnalysisExecutionOutcome(detail(), true))

        mockMvc.perform(
            post("/api/v1/analysis-cases/11/analyze")
                .principal(authentication())
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeRequest()),
        )
            .andExpect(status().isCreated)
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/analyses/31"))
            .andExpect(jsonPath("$.id").value(31))
            .andExpect(jsonPath("$.grade").value("MEDIUM"))
            .andExpect(jsonPath("$.ruleVersion").value(ANALYSIS_RULE_VERSION))

        verify(service).analyze(7L, 11L, "request-1", command)
    }

    @Test
    fun `returns ok for an idempotent replay`() {
        `when`(service.analyze(7L, 11L, "request-1", completeCommand()))
            .thenReturn(AnalysisExecutionOutcome(detail(), false))

        mockMvc.perform(
            post("/api/v1/analysis-cases/11/analyze")
                .principal(authentication())
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeRequest()),
        )
            .andExpect(status().isOk)
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.id").value(31))
    }

    @Test
    fun `returns invalid request for a missing idempotency key`() {
        `when`(service.analyze(7L, 11L, "", completeCommand()))
            .thenThrow(InvalidAnalysisExecutionRequestException())

        mockMvc.perform(
            post("/api/v1/analysis-cases/11/analyze")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeRequest()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test
    fun `rejects invalid monetary facts before calling the service`() {
        mockMvc.perform(
            post("/api/v1/analysis-cases/11/analyze")
                .principal(authentication())
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"estimatedPropertyValueManwon":0}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))

        verifyNoInteractions(service)
    }

    @Test
    fun `maps a hidden case and completed analysis conflict`() {
        `when`(service.analyze(7L, 11L, "missing", completeCommand()))
            .thenThrow(AnalysisCaseNotFoundException())
        `when`(service.analyze(7L, 11L, "completed", completeCommand()))
            .thenThrow(AnalysisAlreadyCompletedException())

        mockMvc.perform(
            post("/api/v1/analysis-cases/11/analyze")
                .principal(authentication())
                .header("Idempotency-Key", "missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeRequest()),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ANALYSIS_CASE_NOT_FOUND"))

        mockMvc.perform(
            post("/api/v1/analysis-cases/11/analyze")
                .principal(authentication())
                .header("Idempotency-Key", "completed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeRequest()),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ANALYSIS_ALREADY_COMPLETED"))
    }

    @Test
    fun `rejects an unknown enum value`() {
        mockMvc.perform(
            post("/api/v1/analysis-cases/11/analyze")
                .principal(authentication())
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"seniorRightStatus":"INVALID"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))

        verifyNoInteractions(service)
    }

    private fun completeRequest() =
        """
        {
          "estimatedPropertyValueManwon": 30000,
          "seniorClaimAmountManwon": 0,
          "seniorRightStatus": "NONE",
          "depositGuaranteeStatus": "ENROLLED",
          "ownershipStatus": "MATCHED",
          "seizureOrAuctionStatus": "NONE"
        }
        """.trimIndent()

    private fun completeCommand() =
        AnalysisExecutionCommand(
            estimatedPropertyValueManwon = 30_000L,
            seniorClaimAmountManwon = 0L,
            seniorRightStatus = SeniorRightStatus.NONE,
            depositGuaranteeStatus = DepositGuaranteeStatus.ENROLLED,
            ownershipStatus = OwnershipStatus.MATCHED,
            seizureOrAuctionStatus = SeizureOrAuctionStatus.NONE,
        )

    private fun detail() =
        AnalysisResultDetail(
            id = 31L,
            caseId = 11L,
            propertyId = 3L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            score = 40,
            grade = AnalysisRiskGrade.MEDIUM,
            confidence = 100,
            summary = "확인이 필요한 위험 신호가 있습니다.",
            findings = listOf("유효 담보비율은 83.3%입니다."),
            recommendations = listOf("등기부등본을 확인하세요."),
            ruleVersion = ANALYSIS_RULE_VERSION,
            analyzedAt = Instant.parse("2026-07-24T10:15:30Z"),
        )

    private fun authentication() = UsernamePasswordAuthenticationToken(7L, null)
}
