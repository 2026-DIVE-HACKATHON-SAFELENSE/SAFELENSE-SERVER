// Upstage Chat Completions API에 엄격한 JSON Schema 해석을 요청하고 결과를 파싱하는 HTTP 어댑터
package com.safelense.analysis.interpretation

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class UpstageReportUnavailableException(val reason: String) : RuntimeException(reason)

@Component
class UpstageHttpReportClient(
    restClientBuilder: RestClient.Builder,
    private val properties: UpstageProperties,
    private val objectMapper: ObjectMapper,
) : OpenAiReportClient {
    private val restClient = restClientBuilder.build()

    override fun generate(request: OpenAiReportRequest): AiReportResult = try {
        val response = restClient.post()
            .uri("${properties.baseUrl}/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody(request))
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw UpstageReportUnavailableException("EMPTY_RESPONSE")
        parseReport(extractMessageContent(response))
    } catch (exception: RestClientResponseException) {
        logger.warn("Upstage request failed. httpStatus={}", exception.statusCode.value())
        throw UpstageReportUnavailableException("HTTP_${exception.statusCode.value()}")
    } catch (exception: RestClientException) {
        logger.warn("Upstage request failed. reason={}", exception.javaClass.simpleName)
        throw UpstageReportUnavailableException(exception.javaClass.simpleName)
    } catch (exception: UpstageReportUnavailableException) {
        throw exception
    } catch (exception: Exception) {
        logger.warn("Upstage response handling failed. reason={}", exception.javaClass.simpleName)
        throw UpstageReportUnavailableException("INVALID_RESPONSE")
    }

    private fun requestBody(request: OpenAiReportRequest): Map<String, Any> =
        mapOf(
            "model" to properties.model,
            "messages" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to
                        "주어진 JSON 근거와 규칙 결과만 사용하세요. 각 문장은 evidence- 또는 case- ID를 인용하되 근거 ID는 evidenceIds 필드에만 넣고 text에는 쓰지 마세요. 숫자는 인용한 valueJson에 그대로 존재할 때만 text에 쓰세요. 법률 결론을 단정하지 마세요. attentionLevel과 mitigationStatus는 제공된 근거로 판단할 수 없으면 UNKNOWN으로 답하세요. case- 사례는 유사 대응 패턴 설명에만 사용하고 계약 안전성, 사고 확률, 보증금 반환 가능성의 근거로 사용하지 마세요.",
                ),
                mapOf(
                    "role" to "user",
                    "content" to objectMapper.writeValueAsString(request),
                ),
            ),
            "max_tokens" to 1200,
            "response_format" to mapOf(
                "type" to "json_schema",
                "json_schema" to mapOf(
                    "name" to "contract_decision_report",
                    "strict" to true,
                    "schema" to REPORT_SCHEMA,
                ),
            ),
        )

    private fun extractMessageContent(response: JsonNode): String =
        response.get("choices")?.get(0)?.get("message")?.get("content")?.asString()
            ?: throw UpstageReportUnavailableException("MISSING_MESSAGE_CONTENT")

    private fun parseReport(text: String): AiReportResult {
        val root = objectMapper.readTree(text)
        return AiReportResult(
            summary = root.get("summary").toStatement(),
            attentionLevel = root.get("attentionLevel")?.asString()
                ?.let(AiAttentionLevel::valueOf)
                ?: throw UpstageReportUnavailableException("MISSING_ATTENTION_LEVEL"),
            mitigationStatus = root.get("mitigationStatus")?.asString()
                ?.let(AiMitigationStatus::valueOf)
                ?: throw UpstageReportUnavailableException("MISSING_MITIGATION_STATUS"),
            residentialImpacts = root.get("residentialImpacts").toStatements(),
            actionGuide = root.get("actionGuide").toStatements(),
        )
    }

    private fun JsonNode?.toStatements(): List<EvidenceBackedStatement> =
        this?.values()?.map { it.toStatement() }
            ?: throw UpstageReportUnavailableException("MISSING_STATEMENTS")

    private fun JsonNode?.toStatement(): EvidenceBackedStatement {
        val node = this ?: throw UpstageReportUnavailableException("MISSING_STATEMENT")
        val text = node.get("text")?.asString()
            ?: throw UpstageReportUnavailableException("MISSING_STATEMENT_TEXT")
        val evidenceIds = node.get("evidenceIds")?.values()?.map { it.asString() }
            ?: throw UpstageReportUnavailableException("MISSING_EVIDENCE_IDS")
        return EvidenceBackedStatement(text, evidenceIds)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UpstageHttpReportClient::class.java)
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
                "attentionLevel" to mapOf(
                    "type" to "string",
                    "enum" to AiAttentionLevel.entries.map { it.name },
                ),
                "mitigationStatus" to mapOf(
                    "type" to "string",
                    "enum" to AiMitigationStatus.entries.map { it.name },
                ),
                "residentialImpacts" to mapOf("type" to "array", "items" to STATEMENT_SCHEMA),
                "actionGuide" to mapOf("type" to "array", "items" to STATEMENT_SCHEMA),
            ),
            "required" to listOf(
                "summary",
                "attentionLevel",
                "mitigationStatus",
                "residentialImpacts",
                "actionGuide",
            ),
        )
    }
}
