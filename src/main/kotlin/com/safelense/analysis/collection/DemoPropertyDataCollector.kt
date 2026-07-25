// 정규 주소에 연결된 내부 시드로 데모 근거와 명시적 누락 상태를 만드는 수집기
package com.safelense.analysis.collection

import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.property.HomeProperty
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

private const val DEMO_SOURCE = "DEMO"
private const val DEMO_SOURCE_IDENTIFIER = "demo-seed-2026-v1"
private const val SEEDED_ADDRESS = "서울특별시 중구 세종대로 110"
private val DEMO_AS_OF = Instant.parse("2026-07-01T00:00:00Z")
private val EVIDENCE_KEYS = listOf(
    "OFFICIAL_PRICE",
    "TRANSACTION_PRICE",
    "JEONSE_RATIO",
    "URBAN_PLAN",
    "REDEVELOPMENT_PLAN",
    "FLOOD_HISTORY",
    "DEVELOPMENT_PROJECT",
    "DEPOSIT_INSURANCE_ELIGIBILITY",
)

private data class DemoEvidenceValue(
    val valueJson: String?,
    val status: EvidenceStatus,
    val confidence: Int,
)

@Component
class DemoPropertyDataCollector(
    private val clock: Clock = Clock.systemUTC(),
) : PropertyDataCollector {
    override fun collect(property: HomeProperty): List<CollectedEvidenceCommand> {
        val seed = if (property.address.normalizeAddress() == SEEDED_ADDRESS) SEEDED_EVIDENCE else emptyMap()
        val collectedAt = Instant.now(clock)
        return EVIDENCE_KEYS.map { evidenceKey ->
            val value = seed[evidenceKey] ?: missingValue(evidenceKey)
            CollectedEvidenceCommand(
                evidenceKey = evidenceKey,
                valueJson = value.valueJson,
                source = DEMO_SOURCE,
                sourceIdentifier = DEMO_SOURCE_IDENTIFIER,
                asOf = DEMO_AS_OF,
                collectedAt = collectedAt,
                confidence = value.confidence,
                status = value.status,
            )
        }
    }

    private fun String.normalizeAddress(): String = trim().replace(Regex("\\s+"), " ")

    private fun missingValue(evidenceKey: String): DemoEvidenceValue =
        DemoEvidenceValue(
            valueJson = null,
            status = if (evidenceKey in UNAVAILABLE_KEYS) EvidenceStatus.UNAVAILABLE else EvidenceStatus.NOT_AVAILABLE,
            confidence = 0,
        )

    companion object {
        private val UNAVAILABLE_KEYS = setOf("TRANSACTION_PRICE", "DEPOSIT_INSURANCE_ELIGIBILITY")
        private val SEEDED_EVIDENCE = mapOf(
            "OFFICIAL_PRICE" to DemoEvidenceValue(
                """{"amount":48000,"unit":"TEN_THOUSAND_KRW"}""",
                EvidenceStatus.AVAILABLE,
                90,
            ),
            "TRANSACTION_PRICE" to DemoEvidenceValue(
                """{"amount":52000,"unit":"TEN_THOUSAND_KRW"}""",
                EvidenceStatus.AVAILABLE,
                80,
            ),
            "JEONSE_RATIO" to DemoEvidenceValue("""{"ratio":0.82}""", EvidenceStatus.AVAILABLE, 85),
            "URBAN_PLAN" to DemoEvidenceValue(
                """{"designation":"GENERAL_RESIDENTIAL"}""",
                EvidenceStatus.AVAILABLE,
                85,
            ),
            "FLOOD_HISTORY" to DemoEvidenceValue("""{"reportedCount":0}""", EvidenceStatus.AVAILABLE, 70),
        )
    }
}
