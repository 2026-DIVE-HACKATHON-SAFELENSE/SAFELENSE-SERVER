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

    @Test
    fun `keeps a multi level city district and selects the legal dong before the lot`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val resolver = VWorldAddressResolver(
            builder,
            VWorldProperties("vworld-key", searchBaseUrl = "https://vworld.test/req/search"),
        )
        server.expect(requestTo(org.hamcrest.Matchers.any(String::class.java)))
            .andRespond(
                withSuccess(
                    SINGLE_ADDRESS_RESPONSE
                        .replace("1114010300100310000", "4111514100101230000")
                        .replace("서울특별시 중구 태평로1가 31", "경기도 수원시 팔달구 인계동 123")
                        .replace("서울특별시 중구 세종대로 110", "경기도 수원시 팔달구 효원로 241"),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = resolver.resolve("경기도 수원시 팔달구 효원로 241")

        assertThat(result?.district).isEqualTo("수원시 팔달구")
        assertThat(result?.legalDong).isEqualTo("인계동")
        server.verify()
    }

    @Test
    fun `falls back to parcel search only when road search has no result`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val resolver = VWorldAddressResolver(
            builder,
            VWorldProperties("vworld-key", searchBaseUrl = "https://vworld.test/req/search"),
        )
        server.expect(requestTo(org.hamcrest.Matchers.containsString("category=road")))
            .andRespond(
                withSuccess(
                    """{"response":{"status":"NOT_FOUND","record":{"total":"0"}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server.expect(requestTo(org.hamcrest.Matchers.containsString("category=parcel")))
            .andRespond(withSuccess(SINGLE_ADDRESS_RESPONSE, MediaType.APPLICATION_JSON))

        val result = resolver.resolve("서울특별시 중구 태평로1가 31")

        assertThat(result?.pnu).isEqualTo("1114010300100310000")
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
