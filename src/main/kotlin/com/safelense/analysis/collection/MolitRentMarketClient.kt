// 국토교통부 주택유형별 전월세 자료에서 같은 법정동의 보증금 분포를 계산하는 어댑터
package com.safelense.analysis.collection

import com.safelense.property.BuildingType
import java.time.YearMonth
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import org.w3c.dom.Element

data class RentMarketSnapshot(
    val sampleCount: Int,
    val medianDepositManwon: Long,
    val minimumDepositManwon: Long,
    val maximumDepositManwon: Long,
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
)

fun interface RentMarketClient {
    fun fetch(
        address: ResolvedPropertyAddress,
        buildingType: BuildingType,
        months: List<YearMonth>,
    ): RentMarketSnapshot?
}

@Component
class MolitRentMarketHttpClient(
    restClientBuilder: RestClient.Builder,
    private val properties: PublicDataProperties,
) : RentMarketClient {
    private val restClient = restClientBuilder.build()

    override fun fetch(
        address: ResolvedPropertyAddress,
        buildingType: BuildingType,
        months: List<YearMonth>,
    ): RentMarketSnapshot? {
        val endpoint = endpoint(buildingType) ?: return null
        if (months.isEmpty()) {
            return null
        }
        val deposits = months.flatMap { month -> fetchMonth(endpoint, address, month) }.sorted()
        if (deposits.isEmpty()) {
            return null
        }
        val middle = deposits.size / 2
        val median =
            if (deposits.size % 2 == 0) {
                (deposits[middle - 1] + deposits[middle]) / 2
            } else {
                deposits[middle]
            }
        return RentMarketSnapshot(
            sampleCount = deposits.size,
            medianDepositManwon = median,
            minimumDepositManwon = deposits.first(),
            maximumDepositManwon = deposits.last(),
            fromMonth = months.min(),
            toMonth = months.max(),
        )
    }

    private fun endpoint(buildingType: BuildingType): Pair<String, String>? =
        when (buildingType) {
            BuildingType.APARTMENT ->
                properties.apartmentRentBaseUrl to "getRTMSDataSvcAptRent"
            BuildingType.OFFICETEL ->
                properties.officetelRentBaseUrl to "getRTMSDataSvcOffiRent"
            BuildingType.DETACHED_HOUSE ->
                properties.detachedRentBaseUrl to "getRTMSDataSvcSHRent"
            BuildingType.MULTI_FAMILY -> null
        }

    private fun fetchMonth(
        endpoint: Pair<String, String>,
        address: ResolvedPropertyAddress,
        month: YearMonth,
    ): List<Long> {
        val uri = UriComponentsBuilder.fromUriString("${endpoint.first}/${endpoint.second}")
            .queryParam("serviceKey", properties.serviceKey)
            .queryParam("LAWD_CD", address.sigunguCode)
            .queryParam("DEAL_YMD", month.toString().replace("-", ""))
            .queryParam("numOfRows", 1000)
            .queryParam("pageNo", 1)
            .build()
            .encode()
            .toUri()
        val xml = restClient.get().uri(uri).retrieve().body(String::class.java) ?: return emptyList()
        val document = SafeXml.parse(xml)
        val resultCode = document.getElementsByTagName("resultCode").item(0)?.textContent?.trim()
        if (resultCode !in setOf("00", "000", "0000")) {
            throw IllegalStateException("Rent market provider rejected the request")
        }
        val items = document.getElementsByTagName("item")
        return (0 until items.length).mapNotNull { index ->
            val item = items.item(index) as? Element ?: return@mapNotNull null
            val legalDong = item.childText("umdNm", "법정동")
            if (legalDong != address.legalDong) {
                return@mapNotNull null
            }
            item.childText("deposit", "보증금액")
                ?.replace(",", "")
                ?.trim()
                ?.toLongOrNull()
        }
    }

    private fun Element.childText(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            getElementsByTagName(name).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)
        }
}
