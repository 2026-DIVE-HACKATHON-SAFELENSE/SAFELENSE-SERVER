// 계약 전 규칙 엔진이 사용 가능한 수집 근거만 위험 입력으로 변환하는지 검증하는 테스트
package com.safelense.analysis.run

import com.safelense.analysis.AnalysisRiskRuleEngine
import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class AnalysisRiskRuleEngineEvidenceTests {
    private val engine = AnalysisRiskRuleEngine()
    private val objectMapper = ObjectMapper()

    @Test
    fun `uses an available collected price as rule input`() {
        val assessment = engine.assess(property(), listOf(price(EvidenceStatus.AVAILABLE)), objectMapper)

        assertThat(assessment.confidence).isEqualTo(35)
    }

    @Test
    fun `ignores a collected price that is unavailable`() {
        val assessment = engine.assess(property(), listOf(price(EvidenceStatus.UNAVAILABLE)), objectMapper)

        assertThat(assessment.confidence).isZero()
    }

    @Test
    fun `ignores former transaction price and rent market as property value`() {
        val assessment = engine.assess(
            property(),
            listOf(
                evidence("TRANSACTION_PRICE", """{"amount":100000}"""),
                evidence("RENT_MARKET", """{"medianDepositManwon":30000}"""),
            ),
            objectMapper,
        )

        assertThat(assessment.score).isNull()
        assertThat(assessment.confidence).isZero()
    }

    private fun price(status: EvidenceStatus) =
        CollectedEvidenceCommand(
            evidenceKey = "OFFICIAL_PRICE",
            valueJson = """{"amount":50000,"unit":"TEN_THOUSAND_KRW"}""",
            source = "DEMO",
            sourceIdentifier = "demo-seed-2026-v1",
            asOf = Instant.parse("2026-07-01T00:00:00Z"),
            collectedAt = Instant.parse("2026-07-26T00:00:00Z"),
            confidence = if (status == EvidenceStatus.AVAILABLE) 90 else 0,
            status = status,
        )

    private fun evidence(key: String, valueJson: String) =
        CollectedEvidenceCommand(
            evidenceKey = key,
            valueJson = valueJson,
            source = "LIVE_TEST",
            sourceIdentifier = null,
            asOf = Instant.parse("2026-07-01T00:00:00Z"),
            collectedAt = Instant.parse("2026-07-26T00:00:00Z"),
            confidence = 90,
            status = EvidenceStatus.AVAILABLE,
        )

    private fun property() =
        HomeProperty(
            id = 2L,
            userId = 1L,
            address = "서울특별시 중구 세종대로 110",
            depositAmount = 20000,
            buildingType = BuildingType.APARTMENT,
        )
}
