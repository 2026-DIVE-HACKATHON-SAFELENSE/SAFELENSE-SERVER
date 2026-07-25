// OpenAI Embeddings API로 상담 검색용 벡터를 생성하는 HTTP 어댑터
package com.safelense.analysis.match

import com.safelense.analysis.interpretation.OpenAiProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode

@Component
class OpenAiEmbeddingClient(
    restClientBuilder: RestClient.Builder,
    private val properties: OpenAiProperties,
) : EmbeddingClient {
    private val restClient = restClientBuilder.build()

    override fun embed(inputs: List<String>): List<List<Double>> {
        if (inputs.isEmpty()) {
            return emptyList()
        }
        return try {
            val response = restClient.post()
                .uri("${properties.baseUrl}/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("model" to properties.embeddingModel, "input" to inputs))
                .retrieve()
                .body(JsonNode::class.java)
                ?: throw EmbeddingUnavailableException()
            val vectors = response.get("data")?.values()?.map { item ->
                val index = item.get("index")?.asInt() ?: throw EmbeddingUnavailableException()
                val vector = item.get("embedding")?.values()?.map(JsonNode::asDouble)
                    ?: throw EmbeddingUnavailableException()
                index to vector
            }?.sortedBy(Pair<Int, List<Double>>::first)?.map(Pair<Int, List<Double>>::second)
                ?: throw EmbeddingUnavailableException()
            if (vectors.size != inputs.size) {
                throw EmbeddingUnavailableException()
            }
            vectors
        } catch (_: EmbeddingUnavailableException) {
            throw EmbeddingUnavailableException()
        } catch (_: Exception) {
            throw EmbeddingUnavailableException()
        }
    }
}
