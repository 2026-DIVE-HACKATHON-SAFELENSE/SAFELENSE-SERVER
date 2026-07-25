// 공동주택가격 응답을 정확히 한 건 식별할 때만 가격으로 사용하는 테스트
package com.safelense.analysis.collection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class VWorldOfficialPriceClientTests {
    @Test
    fun `maps one official price from won to manwon`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = VWorldOfficialPriceClient(
            builder,
            VWorldProperties(
                "vworld-key",
                officialPriceBaseUrl = "https://vworld.test/official-price",
            ),
        )
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("pnu=1114010300100310000"),
            org.hamcrest.Matchers.containsString("stdrYear=2026"),
            org.hamcrest.Matchers.containsString("key=vworld-key"),
        )))
            .andRespond(
                withSuccess(
                    """{"apartHousingPrices":{"totalCount":"1","field":[{"pblntfPc":"500,000,000","stdrYear":"2026","stdrMt":"01"}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThat(client.fetch(address(), 2026))
            .isEqualTo(OfficialPriceSnapshot(50000L, 2026, 1))
        server.verify()
    }

    @Test
    fun `does not guess an official price when multiple homes remain`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = VWorldOfficialPriceClient(
            builder,
            VWorldProperties(
                "vworld-key",
                officialPriceBaseUrl = "https://vworld.test/official-price",
            ),
        )
        server.expect(requestTo(org.hamcrest.Matchers.any(String::class.java)))
            .andRespond(
                withSuccess(
                    """{"apartHousingPrices":{"totalCount":"2","field":[{"pblntfPc":"500000000"},{"pblntfPc":"600000000"}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThat(client.fetch(address(), 2026)).isNull()
        server.verify()
    }
}
