// OpenAI Embeddings API 요청과 응답 벡터 순서를 검증하는 테스트
package com.safelense.analysis.match

import com.safelense.analysis.interpretation.OpenAiProperties
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OpenAiEmbeddingClientTests {
    @Test
    fun `creates embeddings in input index order`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = OpenAiEmbeddingClient(
            builder,
            OpenAiProperties(
                apiKey = "openai-key",
                baseUrl = "https://openai.test/v1",
                embeddingModel = "text-embedding-3-small",
            ),
        )
        server.expect(requestTo("https://openai.test/v1/embeddings"))
            .andExpect(content().string(containsString("\"model\":\"text-embedding-3-small\"")))
            .andExpect(content().string(containsString("\"input\":[\"첫 상담\",\"둘째 상담\"]")))
            .andRespond(
                withSuccess(
                    """{"data":[{"index":1,"embedding":[0.3,0.4]},{"index":0,"embedding":[0.1,0.2]}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.embed(listOf("첫 상담", "둘째 상담"))

        assertThat(result).containsExactly(listOf(0.1, 0.2), listOf(0.3, 0.4))
        server.verify()
    }
}
