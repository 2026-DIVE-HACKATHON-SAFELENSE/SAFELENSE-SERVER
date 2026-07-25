// VWorld 주소 응답에서 공공데이터 조회용 법정동과 지번 코드를 추출하는 테스트
package com.safelense.analysis.collection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class VWorldAddressResolverTests {
    @Test
    fun `resolves one road address to pnu and building query codes`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val resolver = VWorldAddressResolver(
            builder,
            VWorldProperties("vworld-key", searchBaseUrl = "https://vworld.test/req/search"),
        )
        server.expect(requestTo(org.hamcrest.Matchers.containsString("query=%EC%84%9C%EC%9A%B8")))
            .andRespond(withSuccess(SINGLE_ADDRESS_RESPONSE, MediaType.APPLICATION_JSON))

        val result = resolver.resolve("서울특별시 중구 세종대로 110")

        assertThat(result).isEqualTo(
            ResolvedPropertyAddress(
                pnu = "1114010300100310000",
                sigunguCode = "11140",
                bjdongCode = "10300",
                platGbCode = "0",
                bun = "0031",
                ji = "0000",
                province = "서울특별시",
                district = "중구",
                legalDong = "태평로1가",
                longitude = 126.977829,
                latitude = 37.566317,
            ),
        )
        server.verify()
    }

    @Test
    fun `returns null when the address is ambiguous`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val resolver = VWorldAddressResolver(
            builder,
            VWorldProperties("vworld-key", searchBaseUrl = "https://vworld.test/req/search"),
        )
        server.expect(requestTo(org.hamcrest.Matchers.any(String::class.java)))
            .andRespond(
                withSuccess(
                    """{"response":{"status":"OK","record":{"total":"2"},"result":{"items":[]}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThat(resolver.resolve("세종대로")).isNull()
        server.verify()
    }

    companion object {
        private val SINGLE_ADDRESS_RESPONSE = """
            {
              "response": {
                "status": "OK",
                "record": {"total": "1"},
                "result": {
                  "items": [{
                    "id": "1114010300100310000",
                    "address": {
                      "parcel": "서울특별시 중구 태평로1가 31",
                      "road": "서울특별시 중구 세종대로 110"
                    },
                    "point": {"x": "126.977829", "y": "37.566317"}
                  }]
                }
              }
            }
        """.trimIndent()
    }
}
