// OpenAI Responses API에 엄격한 JSON Schema 해석을 요청하고 결과를 파싱하는 HTTP 어댑터
package com.safelense.analysis.interpretation

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class OpenAiReportUnavailableException : RuntimeException()

@Component
class OpenAiHttpReportClient(
    restClientBuilder: RestClient.Builder,
    private val properties: OpenAiProperties,
    private val objectMapper: ObjectMapper,
) : OpenAiReportClient {
    private val restClient = restClientBuilder.build()

    override fun generate(request: OpenAiReportRequest): AiReportResult = try {
        val response = requireNotNull(
            restClient.post()
                .uri("${properties.baseUrl}/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(request))
                .retrieve()
                .body(JsonNode::class.java),
        )
        parseReport(extractOutputText(response))
    } catch (_: RestClientException) {
        throw OpenAiReportUnavailableException()
    } catch (_: OpenAiReportUnavailableException) {
        throw OpenAiReportUnavailableException()
    } catch (_: Exception) {
        throw OpenAiReportUnavailableException()
    }

    private fun requestBody(request: OpenAiReportRequest): Map<String, Any> =
        mapOf(
            "model" to properties.model,
            "store" to false,
            "instructions" to
                "주어진 JSON 근거와 규칙 결과만 사용하세요. 각 문장은 evidence- 또는 case- ID를 인용하고 법률 결론을 단정하지 마세요.",
            "input" to objectMapper.writeValueAsString(request),
            "max_output_tokens" to 1200,
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to "contract_decision_report",
                    "strict" to true,
                    "schema" to REPORT_SCHEMA,
                ),
            ),
        )

    private fun extractOutputText(response: JsonNode): String {
        val output = response.get("output") ?: throw OpenAiReportUnavailableException()
        output.forEach { item ->
            item.get("content")?.forEach { content ->
                when (content.get("type")?.asString()) {
                    "output_text" -> return content.get("text")?.asString()
                        ?: throw OpenAiReportUnavailableException()
                    "refusal" -> throw OpenAiReportUnavailableException()
                }
            }
        }
        throw OpenAiReportUnavailableException()
    }

    private fun parseReport(text: String): AiReportResult {
        val root = objectMapper.readTree(text)
        return AiReportResult(
            summary = root.get("summary").toStatement(),
            residentialImpacts = root.get("residentialImpacts").toStatements(),
            actionGuide = root.get("actionGuide").toStatements(),
        )
    }

    private fun JsonNode?.toStatements(): List<EvidenceBackedStatement> =
        this?.values()?.map { it.toStatement() } ?: throw OpenAiReportUnavailableException()

    private fun JsonNode?.toStatement(): EvidenceBackedStatement {
        val node = this ?: throw OpenAiReportUnavailableException()
        val text = node.get("text")?.asString() ?: throw OpenAiReportUnavailableException()
        val evidenceIds = node.get("evidenceIds")?.values()?.map { it.asString() }
            ?: throw OpenAiReportUnavailableException()
        return EvidenceBackedStatement(text, evidenceIds)
    }

    companion object {
        private val STATEMENT_SCHEMA = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "text" to mapOf("type" to "string"),
                "evidenceIds" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string"),
                ),
            ),
            "required" to listOf("text", "evidenceIds"),
        )
        private val REPORT_SCHEMA = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "summary" to STATEMENT_SCHEMA,
                "residentialImpacts" to mapOf("type" to "array", "items" to STATEMENT_SCHEMA),
                "actionGuide" to mapOf("type" to "array", "items" to STATEMENT_SCHEMA),
            ),
            "required" to listOf("summary", "residentialImpacts", "actionGuide"),
        )
    }
}
