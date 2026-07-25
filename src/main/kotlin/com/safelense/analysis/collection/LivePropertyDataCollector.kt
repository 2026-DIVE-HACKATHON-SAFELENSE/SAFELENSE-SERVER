// 실제 주소와 공공 API 결과를 실행별 근거로 정규화하는 수집기
package com.safelense.analysis.collection

import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class LivePropertyDataCollector(
    private val addressResolver: PropertyAddressResolver,
    private val buildingClient: BuildingRegisterClient,
    private val priceClient: OfficialPriceClient,
    private val rentClient: RentMarketClient,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) : PropertyDataCollector {
    override fun collect(property: HomeProperty): List<CollectedEvidenceCommand> {
        val collectedAt = Instant.now(clock)
        val address = runCatching { addressResolver.resolve(property.address) }.getOrNull()
            ?: return unresolvedAddressEvidence(collectedAt)
        return listOf(
            available(
                key = "ADDRESS_RESOLUTION",
                source = "VWORLD_ADDRESS",
                sourceIdentifier = null,
                value = mapOf(
                    "province" to address.province,
                    "district" to address.district,
                    "legalDong" to address.legalDong,
                ),
                asOf = null,
                confidence = 100,
                collectedAt = collectedAt,
            ),
            collectBuilding(address, collectedAt),
            collectOfficialPrice(address, collectedAt),
            collectRentMarket(address, property.buildingType, collectedAt),
        ) + unsupportedEvidence(collectedAt)
    }

    private fun collectBuilding(
        address: ResolvedPropertyAddress,
        collectedAt: Instant,
    ): CollectedEvidenceCommand = try {
        val snapshot = buildingClient.fetch(address)
            ?: return missing("BUILDING_REGISTER", "BUILDING_HUB", "getBrTitleInfo", collectedAt)
        available(
            "BUILDING_REGISTER",
            "BUILDING_HUB",
            "getBrTitleInfo",
            snapshot,
            null,
            90,
            collectedAt,
        )
    } catch (_: Exception) {
        unavailable("BUILDING_REGISTER", "BUILDING_HUB", "getBrTitleInfo", collectedAt)
    }

    private fun collectOfficialPrice(
        address: ResolvedPropertyAddress,
        collectedAt: Instant,
    ): CollectedEvidenceCommand = try {
        val year = LocalDate.now(clock).year
        val snapshot = priceClient.fetch(address, year) ?: priceClient.fetch(address, year - 1)
            ?: return missing(
                "OFFICIAL_PRICE",
                "VWORLD_OFFICIAL_PRICE",
                "getApartHousingPriceAttr",
                collectedAt,
            )
        val asOf = LocalDate.of(snapshot.standardYear, snapshot.standardMonth ?: 1, 1)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
        available(
            "OFFICIAL_PRICE",
            "VWORLD_OFFICIAL_PRICE",
            "getApartHousingPriceAttr",
            mapOf(
                "amount" to snapshot.amountManwon,
                "unit" to "MANWON",
                "standardYear" to snapshot.standardYear,
                "standardMonth" to snapshot.standardMonth,
            ),
            asOf,
            95,
            collectedAt,
        )
    } catch (_: Exception) {
        unavailable(
            "OFFICIAL_PRICE",
            "VWORLD_OFFICIAL_PRICE",
            "getApartHousingPriceAttr",
            collectedAt,
        )
    }

    private fun collectRentMarket(
        address: ResolvedPropertyAddress,
        buildingType: BuildingType,
        collectedAt: Instant,
    ): CollectedEvidenceCommand = try {
        val currentMonth = YearMonth.now(clock)
        val months = (5 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
        val snapshot = rentClient.fetch(address, buildingType, months)
            ?: return missing("RENT_MARKET", "MOLIT_RENT_TRANSACTIONS", null, collectedAt)
        val asOf = snapshot.toMonth.atEndOfMonth().atStartOfDay().toInstant(ZoneOffset.UTC)
        available(
            "RENT_MARKET",
            "MOLIT_RENT_TRANSACTIONS",
            buildingType.name,
            snapshot,
            asOf,
            85,
            collectedAt,
        )
    } catch (_: Exception) {
        unavailable("RENT_MARKET", "MOLIT_RENT_TRANSACTIONS", buildingType.name, collectedAt)
    }

    private fun unresolvedAddressEvidence(collectedAt: Instant): List<CollectedEvidenceCommand> =
        listOf(
            unavailable("ADDRESS_RESOLUTION", "VWORLD_ADDRESS", null, collectedAt),
            unavailable("BUILDING_REGISTER", "BUILDING_HUB", "getBrTitleInfo", collectedAt),
            unavailable(
                "OFFICIAL_PRICE",
                "VWORLD_OFFICIAL_PRICE",
                "getApartHousingPriceAttr",
                collectedAt,
            ),
            unavailable("RENT_MARKET", "MOLIT_RENT_TRANSACTIONS", null, collectedAt),
        ) + unsupportedEvidence(collectedAt)

    private fun unsupportedEvidence(collectedAt: Instant): List<CollectedEvidenceCommand> =
        UNSUPPORTED_KEYS.map { key ->
            missing(key, "NOT_SUPPORTED", null, collectedAt)
        }

    private fun available(
        key: String,
        source: String,
        sourceIdentifier: String?,
        value: Any,
        asOf: Instant?,
        confidence: Int,
        collectedAt: Instant,
    ) = CollectedEvidenceCommand(
        evidenceKey = key,
        valueJson = objectMapper.writeValueAsString(value),
        source = source,
        sourceIdentifier = sourceIdentifier,
        asOf = asOf,
        collectedAt = collectedAt,
        confidence = confidence,
        status = EvidenceStatus.AVAILABLE,
    )

    private fun missing(
        key: String,
        source: String,
        sourceIdentifier: String?,
        collectedAt: Instant,
    ) = evidence(key, source, sourceIdentifier, EvidenceStatus.NOT_AVAILABLE, collectedAt)

    private fun unavailable(
        key: String,
        source: String,
        sourceIdentifier: String?,
        collectedAt: Instant,
    ) = evidence(key, source, sourceIdentifier, EvidenceStatus.UNAVAILABLE, collectedAt)

    private fun evidence(
        key: String,
        source: String,
        sourceIdentifier: String?,
        status: EvidenceStatus,
        collectedAt: Instant,
    ) = CollectedEvidenceCommand(
        evidenceKey = key,
        valueJson = null,
        source = source,
        sourceIdentifier = sourceIdentifier,
        asOf = null,
        collectedAt = collectedAt,
        confidence = 0,
        status = status,
    )

    companion object {
        private val UNSUPPORTED_KEYS = listOf(
            "URBAN_PLAN",
            "REDEVELOPMENT_PLAN",
            "FLOOD_HISTORY",
            "DEPOSIT_INSURANCE_ELIGIBILITY",
            "JEONSE_RATIO",
        )
    }
}
