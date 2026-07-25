// 건축HUB 표제부에서 건물 특성을 조회하고 정규화하는 어댑터
package com.safelense.analysis.collection

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode

data class BuildingRegisterSnapshot(
    val mainPurpose: String?,
    val approvalDate: LocalDate?,
    val structure: String?,
    val groundFloors: Int?,
    val undergroundFloors: Int?,
    val violationBuilding: Boolean?,
)

fun interface BuildingRegisterClient {
    fun fetch(address: ResolvedPropertyAddress): BuildingRegisterSnapshot?
}

@Component
class BuildingRegisterHttpClient(
    restClientBuilder: RestClient.Builder,
    private val properties: PublicDataProperties,
) : BuildingRegisterClient {
    private val restClient = restClientBuilder.build()

    override fun fetch(address: ResolvedPropertyAddress): BuildingRegisterSnapshot? {
        val uri = UriComponentsBuilder
            .fromUriString("${properties.buildingBaseUrl}/getBrTitleInfo")
            .queryParam("serviceKey", properties.serviceKey)
            .queryParam("sigunguCd", address.sigunguCode)
            .queryParam("bjdongCd", address.bjdongCode)
            .queryParam("platGbCd", address.platGbCode)
            .queryParam("bun", address.bun)
            .queryParam("ji", address.ji)
            .queryParam("_type", "json")
            .queryParam("numOfRows", 10)
            .queryParam("pageNo", 1)
            .build()
            .encode()
            .toUri()
        val root = restClient.get().uri(uri).retrieve().body(JsonNode::class.java) ?: return null
        val response = root.get("response") ?: return null
        val resultCode = response.get("header")?.get("resultCode")?.asString()
        if (resultCode !in setOf("00", "000", "0000")) {
            throw IllegalStateException("Building register provider rejected the request")
        }
        val itemNode = response.get("body")?.get("items")?.get("item") ?: return null
        val items = if (itemNode.isArray) itemNode.values().toList() else listOf(itemNode)
        if (items.size != 1) {
            return null
        }
        return items.single().let {
            BuildingRegisterSnapshot(
                mainPurpose = it.text("mainPurpsCdNm"),
                approvalDate = it.text("useAprDay")?.let(::parseDate),
                structure = it.text("strctCdNm"),
                groundFloors = it.integer("grndFlrCnt"),
                undergroundFloors = it.integer("ugrndFlrCnt"),
                violationBuilding = when (it.text("violBldYn")) {
                    "Y" -> true
                    "N" -> false
                    else -> null
                },
            )
        }
    }

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()

    private fun JsonNode.text(name: String): String? =
        get(name)?.asString()?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonNode.integer(name: String): Int? =
        get(name)?.let { node ->
            if (node.isIntegralNumber) node.asInt() else node.asString().toIntOrNull()
        }
}
