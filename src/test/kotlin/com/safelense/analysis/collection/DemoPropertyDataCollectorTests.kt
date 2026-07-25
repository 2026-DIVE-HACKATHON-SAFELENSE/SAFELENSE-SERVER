// 정규 주소 기반 데모 수집기가 출처와 누락 상태를 보존하는지 검증하는 테스트
package com.safelense.analysis.collection

import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DemoPropertyDataCollectorTests {
    private val collector = DemoPropertyDataCollector(
        Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `collects all demo evidence keys from a normalized seeded address`() {
        val evidence = collector.collect(property("  서울특별시   중구 세종대로 110  "))

        assertThat(evidence).hasSize(8)
        assertThat(evidence).allMatch { it.source == "DEMO" }
        assertThat(evidence).allMatch { it.asOf != null }
        assertThat(evidence.map { it.evidenceKey })
            .containsExactlyInAnyOrder(
                "OFFICIAL_PRICE",
                "TRANSACTION_PRICE",
                "JEONSE_RATIO",
                "URBAN_PLAN",
                "REDEVELOPMENT_PLAN",
                "FLOOD_HISTORY",
                "DEVELOPMENT_PROJECT",
                "DEPOSIT_INSURANCE_ELIGIBILITY",
            )
        assertThat(evidence.filter { it.status == EvidenceStatus.AVAILABLE }.map { it.evidenceKey })
            .contains("OFFICIAL_PRICE", "URBAN_PLAN")
        assertThat(evidence.map { it.status })
            .contains(EvidenceStatus.NOT_AVAILABLE, EvidenceStatus.UNAVAILABLE)
    }

    @Test
    fun `uses explicit missing statuses when no seed matches the address`() {
        val evidence = collector.collect(property("알 수 없는 주소"))

        assertThat(evidence).hasSize(8)
        assertThat(evidence).noneMatch { it.status == EvidenceStatus.AVAILABLE }
        assertThat(evidence.map { it.status })
            .contains(EvidenceStatus.NOT_AVAILABLE, EvidenceStatus.UNAVAILABLE)
    }

    private fun property(address: String) =
        HomeProperty(
            id = 2L,
            userId = 1L,
            address = address,
            depositAmount = 20000,
            buildingType = BuildingType.APARTMENT,
            landlordName = "개인정보 임대인",
        )
}
