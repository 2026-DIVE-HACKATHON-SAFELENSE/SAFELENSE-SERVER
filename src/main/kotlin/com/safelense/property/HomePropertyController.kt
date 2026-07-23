// 인증 사용자의 내 집 조회·최초 등록·JSON Merge Patch API를 제공하는 컨트롤러
package com.safelense.property

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

data class HomePropertyCreateRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val address: String,
    @field:PositiveOrZero
    val depositAmount: Long,
    val buildingType: BuildingType,
    @field:Size(max = 100)
    val landlordName: String? = null,
    val plannedContractDate: LocalDate? = null,
)

data class HomePropertyResponse(
    val id: Long,
    val address: String,
    val depositAmount: Long,
    val buildingType: BuildingType,
    val landlordName: String?,
    val plannedContractDate: LocalDate?,
)

data class HomePropertyEnvelope(
    val property: HomePropertyResponse?,
)

@RestController
@RequestMapping("/api/v1/me/property")
class HomePropertyController(
    private val homePropertyService: HomePropertyService,
) {
    @GetMapping
    fun get(authentication: Authentication): HomePropertyEnvelope =
        HomePropertyEnvelope(homePropertyService.get(authentication.userId())?.toResponse())

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: HomePropertyCreateRequest,
    ): HomePropertyEnvelope {
        val property = homePropertyService.create(
            authentication.userId(),
            HomePropertyCreateCommand(
                address = request.address,
                depositAmount = request.depositAmount,
                buildingType = request.buildingType,
                landlordName = request.landlordName,
                plannedContractDate = request.plannedContractDate,
            ),
        )
        return HomePropertyEnvelope(property.toResponse())
    }

    @PatchMapping(consumes = ["application/merge-patch+json"])
    fun patch(
        authentication: Authentication,
        @RequestBody document: JsonNode,
    ): HomePropertyEnvelope {
        val property = homePropertyService.patch(authentication.userId(), document.toPatchCommand())
        return HomePropertyEnvelope(property.toResponse())
    }

    private fun Authentication.userId(): Long = principal as Long

    private fun HomeProperty.toResponse(): HomePropertyResponse =
        HomePropertyResponse(
            id = requireNotNull(id),
            address = address,
            depositAmount = depositAmount,
            buildingType = buildingType,
            landlordName = landlordName,
            plannedContractDate = plannedContractDate,
        )

    private fun JsonNode.toPatchCommand(): HomePropertyPatchCommand {
        val allowedFields = setOf(
            "address",
            "depositAmount",
            "buildingType",
            "landlordName",
            "plannedContractDate",
        )
        if (!isObject || size() == 0 || propertyNames().any { it !in allowedFields }) {
            throw InvalidHomePropertyRequestException()
        }

        return HomePropertyPatchCommand(
            address = requiredTextPatch("address", 500),
            depositAmount = depositPatch(),
            buildingType = buildingTypePatch(),
            landlordName = optionalTextPatch("landlordName", 100),
            plannedContractDate = datePatch(),
        )
    }

    private fun JsonNode.requiredTextPatch(fieldName: String, maxLength: Int): FieldPatch<String> {
        if (!has(fieldName)) return FieldPatch.Unchanged
        val value = get(fieldName)
        if (value.isNull || !value.isString) throw InvalidHomePropertyRequestException()
        val text = value.asString().trim()
        if (text.isEmpty() || text.length > maxLength) throw InvalidHomePropertyRequestException()
        return FieldPatch.Set(text)
    }

    private fun JsonNode.optionalTextPatch(fieldName: String, maxLength: Int): FieldPatch<String> {
        if (!has(fieldName)) return FieldPatch.Unchanged
        val value = get(fieldName)
        if (value.isNull) return FieldPatch.Clear
        if (!value.isString) throw InvalidHomePropertyRequestException()
        val text = value.asString().trim()
        if (text.isEmpty() || text.length > maxLength) throw InvalidHomePropertyRequestException()
        return FieldPatch.Set(text)
    }

    private fun JsonNode.depositPatch(): FieldPatch<Long> {
        if (!has("depositAmount")) return FieldPatch.Unchanged
        val value = get("depositAmount")
        if (value.isNull || !value.isIntegralNumber || !value.canConvertToLong()) {
            throw InvalidHomePropertyRequestException()
        }
        val depositAmount = value.asLong()
        if (depositAmount < 0) throw InvalidHomePropertyRequestException()
        return FieldPatch.Set(depositAmount)
    }

    private fun JsonNode.buildingTypePatch(): FieldPatch<BuildingType> {
        if (!has("buildingType")) return FieldPatch.Unchanged
        val value = get("buildingType")
        if (value.isNull || !value.isString) throw InvalidHomePropertyRequestException()
        val buildingType = runCatching { BuildingType.valueOf(value.asString()) }
            .getOrElse { throw InvalidHomePropertyRequestException() }
        return FieldPatch.Set(buildingType)
    }

    private fun JsonNode.datePatch(): FieldPatch<LocalDate> {
        if (!has("plannedContractDate")) return FieldPatch.Unchanged
        val value = get("plannedContractDate")
        if (value.isNull) return FieldPatch.Clear
        if (!value.isString) throw InvalidHomePropertyRequestException()
        val date = try {
            LocalDate.parse(value.asString())
        } catch (_: DateTimeParseException) {
            throw InvalidHomePropertyRequestException()
        }
        return FieldPatch.Set(date)
    }
}
