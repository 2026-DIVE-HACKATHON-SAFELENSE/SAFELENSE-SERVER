// 실제 제공처 결과와 실패를 독립적인 정규화 근거로 만드는 수집기 테스트
package com.safelense.analysis.collection

import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import tools.jackson.databind.ObjectMapper

class LivePropertyDataCollectorTests {
    private val resolver = mock(PropertyAddressResolver::class.java)
    private val buildingClient = mock(BuildingRegisterClient::class.java)
    private val priceClient = mock(OfficialPriceClient::class.java)
    private val rentClient = mock(RentMarketClient::class.java)
    private val collector = LivePropertyDataCollector(
        resolver,
        buildingClient,
        priceClient,
        rentClient,
        ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `collects live evidence and marks unsupported fields explicitly`() {
        `when`(resolver.resolve(PROPERTY.address)).thenReturn(address())
        `when`(buildingClient.fetch(address())).thenReturn(
            BuildingRegisterSnapshot(
                "공동주택",
                LocalDate.parse("2020-01-02"),
                "철근콘크리트구조",
                20,
                3,
                false,
            ),
        )
        `when`(priceClient.fetch(address(), 2026)).thenReturn(OfficialPriceSnapshot(50000, 2026, 1))
        `when`(rentClient.fetch(address(), BuildingType.APARTMENT, MONTHS)).thenReturn(
            RentMarketSnapshot(
                4,
                31500,
                29000,
                34000,
                YearMonth.parse("2026-02"),
                YearMonth.parse("2026-07"),
            ),
        )

        val result = collector.collect(PROPERTY)

        assertThat(result.map { it.evidenceKey }).containsExactly(
            "ADDRESS_RESOLUTION",
            "BUILDING_REGISTER",
            "OFFICIAL_PRICE",
            "RENT_MARKET",
            "URBAN_PLAN",
            "REDEVELOPMENT_PLAN",
            "FLOOD_HISTORY",
            "DEPOSIT_INSURANCE_ELIGIBILITY",
            "JEONSE_RATIO",
        )
        assertThat(result.first { it.evidenceKey == "OFFICIAL_PRICE" }.source)
            .isEqualTo("VWORLD_OFFICIAL_PRICE")
        assertThat(result.first { it.evidenceKey == "OFFICIAL_PRICE" }.valueJson)
            .contains(""""amount":50000""")
        assertThat(result.first { it.evidenceKey == "RENT_MARKET" }.valueJson)
            .contains(""""medianDepositManwon":31500""")
            .doesNotContain("sale", "transactionPrice")
        assertThat(result.drop(4)).allMatch { it.status == EvidenceStatus.NOT_AVAILABLE }
        assertThat(result).noneMatch { it.source == "DEMO" }
    }

    @Test
    fun `isolates one provider failure from other live evidence`() {
        `when`(resolver.resolve(PROPERTY.address)).thenReturn(address())
        `when`(buildingClient.fetch(address())).thenThrow(IllegalStateException("provider failed"))
        `when`(priceClient.fetch(address(), 2026)).thenReturn(OfficialPriceSnapshot(50000, 2026, 1))
        `when`(rentClient.fetch(address(), BuildingType.APARTMENT, MONTHS)).thenReturn(null)

        val result = collector.collect(PROPERTY)

        assertThat(result.first { it.evidenceKey == "BUILDING_REGISTER" }.status)
            .isEqualTo(EvidenceStatus.UNAVAILABLE)
        assertThat(result.first { it.evidenceKey == "OFFICIAL_PRICE" }.status)
            .isEqualTo(EvidenceStatus.AVAILABLE)
        assertThat(result.first { it.evidenceKey == "RENT_MARKET" }.status)
            .isEqualTo(EvidenceStatus.NOT_AVAILABLE)
    }

    @Test
    fun `uses the previous year when the current official price is not published`() {
        `when`(resolver.resolve(PROPERTY.address)).thenReturn(address())
        `when`(priceClient.fetch(address(), 2026)).thenReturn(null)
        `when`(priceClient.fetch(address(), 2025)).thenReturn(OfficialPriceSnapshot(48000, 2025, 1))

        val result = collector.collect(PROPERTY)

        assertThat(result.first { it.evidenceKey == "OFFICIAL_PRICE" }.valueJson)
            .contains(""""amount":48000""", """"standardYear":2025""")
    }

    @Test
    fun `does not call providers when address resolution fails`() {
        `when`(resolver.resolve(PROPERTY.address)).thenReturn(null)

        val result = collector.collect(PROPERTY)

        assertThat(result.take(4)).allMatch { it.status == EvidenceStatus.UNAVAILABLE }
        assertThat(result.drop(4)).allMatch { it.status == EvidenceStatus.NOT_AVAILABLE }
        verifyNoInteractions(buildingClient, priceClient, rentClient)
    }

    companion object {
        private val NOW = Instant.parse("2026-07-26T00:00:00Z")
        private val MONTHS = listOf(
            YearMonth.parse("2026-02"),
            YearMonth.parse("2026-03"),
            YearMonth.parse("2026-04"),
            YearMonth.parse("2026-05"),
            YearMonth.parse("2026-06"),
            YearMonth.parse("2026-07"),
        )
        private val PROPERTY = HomeProperty(
            id = 2L,
            userId = 1L,
            address = "서울특별시 중구 세종대로 110",
            depositAmount = 20000,
            buildingType = BuildingType.APARTMENT,
        )
    }
}
