// 국토교통부 주택유형별 전월세 응답의 보증금 분포 계산을 검증하는 테스트
package com.safelense.analysis.collection

import com.safelense.property.BuildingType
import java.time.YearMonth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class MolitRentMarketClientTests {
    @Test
    fun `aggregates apartment deposits for the same legal dong`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = MolitRentMarketHttpClient(
            builder,
            PublicDataProperties(
                "public-key",
                apartmentRentBaseUrl = "https://public.test/apartment",
            ),
        )
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("/getRTMSDataSvcAptRent"),
            org.hamcrest.Matchers.containsString("LAWD_CD=11140"),
            org.hamcrest.Matchers.containsString("DEAL_YMD=202606"),
        )))
            .andRespond(withSuccess(rentResponse("29,000", "31,000"), XML_UTF8))
        server.expect(requestTo(org.hamcrest.Matchers.containsString("DEAL_YMD=202607")))
            .andRespond(withSuccess(rentResponse("32,000", "34,000"), XML_UTF8))

        val result = client.fetch(
            address(),
            BuildingType.APARTMENT,
            listOf(YearMonth.parse("2026-06"), YearMonth.parse("2026-07")),
        )

        assertThat(result).isEqualTo(
            RentMarketSnapshot(
                sampleCount = 4,
                medianDepositManwon = 31500,
                minimumDepositManwon = 29000,
                maximumDepositManwon = 34000,
                fromMonth = YearMonth.parse("2026-06"),
                toMonth = YearMonth.parse("2026-07"),
            ),
        )
        server.verify()
    }

    @Test
    fun `does not call a rent API for unsupported multi family homes`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = MolitRentMarketHttpClient(builder, PublicDataProperties("public-key"))

        assertThat(
            client.fetch(address(), BuildingType.MULTI_FAMILY, listOf(YearMonth.parse("2026-07"))),
        ).isNull()
        server.verify()
    }

    @Test
    fun `reads every result page before calculating the rent market`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = MolitRentMarketHttpClient(
            builder,
            PublicDataProperties(
                "public-key",
                apartmentRentBaseUrl = "https://public.test/apartment",
            ),
        )
        server.expect(requestTo(org.hamcrest.Matchers.containsString("pageNo=1")))
            .andRespond(withSuccess(rentResponse("10,000", "20,000", 1001), XML_UTF8))
        server.expect(requestTo(org.hamcrest.Matchers.containsString("pageNo=2")))
            .andRespond(withSuccess(rentResponse("30,000", "40,000", 1001), XML_UTF8))

        val result = client.fetch(
            address(),
            BuildingType.APARTMENT,
            listOf(YearMonth.parse("2026-07")),
        )

        assertThat(result?.sampleCount).isEqualTo(4)
        assertThat(result?.medianDepositManwon).isEqualTo(25000)
        server.verify()
    }

    private fun rentResponse(first: String, second: String, totalCount: Int = 3) = """
        <response>
          <header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
          <body><items>
            <item><umdNm>태평로1가</umdNm><deposit>$first</deposit></item>
            <item><umdNm>태평로1가</umdNm><deposit>$second</deposit></item>
            <item><umdNm>다른동</umdNm><deposit>99,000</deposit></item>
          </items><totalCount>$totalCount</totalCount></body>
        </response>
    """.trimIndent()

    companion object {
        private val XML_UTF8 = MediaType.parseMediaType("application/xml;charset=UTF-8")
    }
}
