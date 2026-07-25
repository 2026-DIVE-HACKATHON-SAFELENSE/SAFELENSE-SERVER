// VWorld 주소 검색으로 법정동 코드와 PNU를 해석하는 HTTP 어댑터
package com.safelense.analysis.collection

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode

@Component
class VWorldAddressResolver(
    restClientBuilder: RestClient.Builder,
    private val properties: VWorldProperties,
) : PropertyAddressResolver {
    private val restClient = restClientBuilder.build()

    override fun resolve(address: String): ResolvedPropertyAddress? {
        if (address.isBlank()) {
            return null
        }
        val road = search(address.trim(), "road")
        val item = when (road.total) {
            1 -> road.item
            0 -> search(address.trim(), "parcel").takeIf { it.total == 1 }?.item
            else -> null
        } ?: return null
        return item.toResolvedAddress()
    }

    private fun search(address: String, category: String): SearchResult {
        val uri = UriComponentsBuilder.fromUriString(properties.searchBaseUrl)
            .queryParam("service", "search")
            .queryParam("request", "search")
            .queryParam("version", "2.0")
            .queryParam("crs", "EPSG:4326")
            .queryParam("size", 2)
            .queryParam("page", 1)
            .queryParam("query", address)
            .queryParam("type", "address")
            .queryParam("category", category)
            .queryParam("format", "json")
            .queryParam("errorformat", "json")
            .queryParam("key", properties.apiKey)
            .build()
            .encode()
            .toUri()
        val root = restClient.get().uri(uri).retrieve().body(JsonNode::class.java)
            ?: return SearchResult(-1, null)
        val response = root.get("response") ?: return SearchResult(-1, null)
        val status = response.get("status")?.asString()
        if (status == "NOT_FOUND") {
            return SearchResult(0, null)
        }
        if (status != "OK") {
            return SearchResult(-1, null)
        }
        val total = response.get("record")?.get("total")?.asString()?.toIntOrNull()
            ?: return SearchResult(-1, null)
        val item = response.get("result")?.get("items")?.values()?.singleOrNull()
        return SearchResult(total, item)
    }

    private fun JsonNode.toResolvedAddress(): ResolvedPropertyAddress? {
        val pnu = get("id")?.asString()?.takeIf { it.length == 19 } ?: return null
        val parcelParts = get("address")?.get("parcel")?.asString()?.trim()?.split(Regex("\\s+"))
            ?: return null
        if (parcelParts.size < 3) {
            return null
        }
        val legalDongIndex =
            if (parcelParts[parcelParts.lastIndex - 1] == "산") {
                parcelParts.lastIndex - 2
            } else {
                parcelParts.lastIndex - 1
            }
        if (legalDongIndex < 1) {
            return null
        }
        val pnuLandCode = pnu.substring(10, 11)
        val platGbCode = when (pnuLandCode) {
            "1" -> "0"
            "2" -> "1"
            else -> return null
        }
        return ResolvedPropertyAddress(
            pnu = pnu,
            sigunguCode = pnu.substring(0, 5),
            bjdongCode = pnu.substring(5, 10),
            platGbCode = platGbCode,
            bun = pnu.substring(11, 15),
            ji = pnu.substring(15, 19),
            province = parcelParts[0],
            district = parcelParts.subList(1, legalDongIndex).joinToString(" "),
            legalDong = parcelParts[legalDongIndex],
            longitude = get("point")?.get("x")?.asString()?.toDoubleOrNull() ?: return null,
            latitude = get("point")?.get("y")?.asString()?.toDoubleOrNull() ?: return null,
        )
    }

    private data class SearchResult(
        val total: Int,
        val item: JsonNode?,
    )
}
