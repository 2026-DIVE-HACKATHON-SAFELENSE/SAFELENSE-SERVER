// 건축HUB 표제부 응답의 리포트 필드 정규화를 검증하는 테스트
package com.safelense.analysis.collection

import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class BuildingRegisterClientTests {
    @Test
    fun `maps a building title response`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = BuildingRegisterHttpClient(
            builder,
            PublicDataProperties("public-key", buildingBaseUrl = "https://public.test/building"),
        )
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("/getBrTitleInfo"),
            org.hamcrest.Matchers.containsString("sigunguCd=11140"),
            org.hamcrest.Matchers.containsString("bjdongCd=10300"),
            org.hamcrest.Matchers.containsString("_type=json"),
        )))
            .andRespond(withSuccess(BUILDING_RESPONSE, MediaType.APPLICATION_JSON))

        val result = client.fetch(address())

        assertThat(result).isEqualTo(
            BuildingRegisterSnapshot(
                mainPurpose = "업무시설",
                approvalDate = LocalDate.parse("2020-01-02"),
                structure = "철근콘크리트구조",
                groundFloors = 20,
                undergroundFloors = 3,
                violationBuilding = false,
            ),
        )
        server.verify()
    }

    @Test
    fun `does not choose the first title when multiple buildings remain`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = BuildingRegisterHttpClient(
            builder,
            PublicDataProperties("public-key", buildingBaseUrl = "https://public.test/building"),
        )
        server.expect(requestTo(org.hamcrest.Matchers.any(String::class.java)))
            .andRespond(
                withSuccess(
                    """
                        {
                          "response": {
                            "header": {"resultCode": "00"},
                            "body": {"items": {"item": [
                              {"mainPurpsCdNm": "업무시설"},
                              {"mainPurpsCdNm": "공동주택"}
                            ]}}
                          }
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThat(client.fetch(address())).isNull()
        server.verify()
    }

    companion object {
        private val BUILDING_RESPONSE = """
            {
              "response": {
                "header": {"resultCode": "00"},
                "body": {
                  "items": {
                    "item": {
                      "mainPurpsCdNm": "업무시설",
                      "useAprDay": "20200102",
                      "strctCdNm": "철근콘크리트구조",
                      "grndFlrCnt": 20,
                      "ugrndFlrCnt": 3,
                      "violBldYn": "N"
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
