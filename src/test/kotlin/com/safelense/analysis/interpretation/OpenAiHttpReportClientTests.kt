// OpenAI Responses API의 구조화 출력 요청과 output_text 파싱을 검증하는 테스트
package com.safelense.analysis.interpretation

import com.safelense.analysis.AnalysisRiskAssessment
import com.safelense.analysis.AnalysisRiskGrade
import com.safelense.analysis.evidence.EvidenceStatus
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

class OpenAiHttpReportClientTests {
    @Test
    fun `requests a non stored strict JSON schema response and parses output text`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val objectMapper = ObjectMapper()
        val client = OpenAiHttpReportClient(
            builder,
            OpenAiProperties("test-key", "gpt-5.6", "https://api.openai.com/v1"),
            objectMapper,
        )
        val resultJson =
            """{"summary":{"text":"가격을 확인하세요.","evidenceIds":["evidence-11"]},"residentialImpacts":[],"actionGuide":[]}"""
        val responseJson =
            """{"output":[{"type":"message","content":[{"type":"output_text","text":${objectMapper.writeValueAsString(resultJson)}}]}]}"""
        server.expect(requestTo("https://api.openai.com/v1/responses"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
            .andExpect(content().string(containsString("\"model\":\"gpt-5.6\"")))
            .andExpect(content().string(containsString("\"store\":false")))
            .andExpect(content().string(containsString("\"type\":\"json_schema\"")))
            .andExpect(content().string(containsString("\"strict\":true")))
            .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

        val result = client.generate(request())

        assertThat(result.summary.text).isEqualTo("가격을 확인하세요.")
        assertThat(result.summary.evidenceIds).containsExactly("evidence-11")
        server.verify()
    }

    private fun request() =
        OpenAiReportRequest(
            facts = listOf(
                AiEvidenceFact(
                    id = "evidence-11",
                    evidenceKey = "OFFICIAL_PRICE",
                    valueJson = """{"amount":50000}""",
                    source = "VWORLD_OFFICIAL_PRICE",
                    asOf = null,
                    status = EvidenceStatus.AVAILABLE,
                ),
            ),
            ruleResult = AnalysisRiskAssessment(
                score = null,
                grade = AnalysisRiskGrade.UNKNOWN,
                confidence = 35,
                summary = "규칙 요약",
                findings = emptyList(),
                recommendations = listOf("가격을 확인하세요."),
                ruleVersion = "dive-2026-v1",
            ),
            matchedCases = emptyList(),
        )
}
