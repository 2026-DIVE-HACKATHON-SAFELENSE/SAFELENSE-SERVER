// VWorld 공동주택가격 속성에서 단일 공시가격을 조회하는 어댑터
package com.safelense.analysis.collection

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode

data class OfficialPriceSnapshot(
    val amountManwon: Long,
    val standardYear: Int,
    val standardMonth: Int?,
)

fun interface OfficialPriceClient {
    fun fetch(address: ResolvedPropertyAddress, year: Int): OfficialPriceSnapshot?
}

@Component
class VWorldOfficialPriceClient(
    restClientBuilder: RestClient.Builder,
    private val properties: VWorldProperties,
) : OfficialPriceClient {
    private val restClient = restClientBuilder.build()

    override fun fetch(address: ResolvedPropertyAddress, year: Int): OfficialPriceSnapshot? {
        val uri = UriComponentsBuilder.fromUriString(properties.officialPriceBaseUrl)
            .queryParam("pnu", address.pnu)
            .queryParam("stdrYear", year)
            .queryParam("format", "json")
            .queryParam("numOfRows", 1000)
            .queryParam("pageNo", 1)
            .queryParam("key", properties.apiKey)
            .build()
            .encode()
            .toUri()
        val root = restClient.get().uri(uri).retrieve().body(JsonNode::class.java) ?: return null
        val prices = root.get("apartHousingPrices") ?: return null
        val total = prices.get("totalCount")?.asString()?.toIntOrNull() ?: return null
        val fields = prices.get("field")?.let { node ->
            if (node.isArray) node.values().toList() else listOf(node)
        }.orEmpty()
        if (total != 1 || fields.size != 1) {
            return null
        }
        val field = fields.single()
        val won = field.get("pblntfPc")?.asString()?.replace(",", "")?.toLongOrNull() ?: return null
        val standardYear = field.get("stdrYear")?.asString()?.toIntOrNull() ?: year
        val standardMonth = field.get("stdrMt")?.asString()?.toIntOrNull()
        return OfficialPriceSnapshot(won / 10_000L, standardYear, standardMonth)
    }
}
