// 인증 사용자의 내 집 조회·최초 등록·JSON Merge Patch API를 제공하는 컨트롤러
package com.safelense.property

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
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

@Schema(description = "내 집 최초 등록 요청")
data class HomePropertyCreateRequest(
    @field:NotBlank
    @field:Size(max = 500)
    @field:Schema(description = "주택 주소", example = "서울특별시 강남구 테헤란로 1")
    val address: String,
    @field:Positive
    @field:Schema(description = "전세 보증금. 단위는 만원", example = "20000")
    val depositAmount: Long,
    @field:Schema(description = "주택 유형", example = "APARTMENT")
    val buildingType: BuildingType,
    @field:Size(max = 100)
    @field:Schema(description = "임대인 이름", example = "홍길동")
    val landlordName: String? = null,
    @field:Schema(description = "계약 예정일", example = "2026-08-01")
    val plannedContractDate: LocalDate? = null,
)

@Schema(description = "등록된 내 집 정보")
data class HomePropertyResponse(
    @field:Schema(description = "내 집 정보 ID", example = "42")
    val id: Long,
    @field:Schema(description = "주택 주소")
    val address: String,
    @field:Schema(description = "전세 보증금. 단위는 만원")
    val depositAmount: Long,
    @field:Schema(description = "주택 유형")
    val buildingType: BuildingType,
    val landlordName: String?,
    val plannedContractDate: LocalDate?,
)

@Schema(description = "내 집 조회 또는 저장 응답")
data class HomePropertyEnvelope(
    @field:Schema(description = "등록된 내 집 정보. 미등록 상태이면 null")
    val property: HomePropertyResponse?,
)

@Tag(name = "내 집", description = "분석 기준이 되는 사용자의 주택 정보를 조회하고 관리합니다.")
@RestController
@RequestMapping("/api/v1/me/property")
class HomePropertyController(
    private val homePropertyService: HomePropertyService,
) {
    @Operation(summary = "내 집 조회", description = "등록된 내 집 정보가 없으면 property가 null인 정상 응답을 반환합니다.")
    @GetMapping
    fun get(authentication: Authentication): HomePropertyEnvelope =
        HomePropertyEnvelope(homePropertyService.get(authentication.userId())?.toResponse())

    @Operation(summary = "내 집 최초 등록", description = "분석에 사용할 주택 정보를 처음 등록합니다. 이미 등록된 경우 충돌 오류를 반환합니다.")
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

    @Operation(summary = "내 집 부분 수정", description = "application/merge-patch+json 형식으로 전달된 필드만 수정합니다. 선택 필드의 null은 값을 삭제합니다.")
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
