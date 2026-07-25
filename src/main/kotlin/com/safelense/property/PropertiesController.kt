// 인증 사용자의 후보 매물 목록을 제공하는 컨트롤러
package com.safelense.property

import com.safelense.auth.presentation.ApiError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@Schema(description = "후보 매물 목록 응답")
data class PropertiesEnvelope(
    @field:Schema(description = "인증 사용자가 등록한 후보 매물 목록")
    val properties: List<HomePropertyResponse>,
)

@Tag(name = "후보 매물", description = "계약 전 비교 분석에 사용할 여러 후보 매물을 관리합니다.")
@RestController
@RequestMapping("/api/v1/properties")
class PropertiesController(
    private val service: HomePropertyService,
) {
    @Operation(summary = "후보 매물 목록 조회", description = "인증 사용자가 등록한 모든 후보 매물을 최신 등록순으로 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "후보 매물 목록 조회 성공",
                content = [Content(schema = Schema(implementation = PropertiesEnvelope::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
        ],
    )
    @GetMapping
    fun list(authentication: Authentication): PropertiesEnvelope =
        PropertiesEnvelope(service.list(authentication.principal as Long).map(::toResponse))

    @Operation(summary = "후보 매물 상세 조회", description = "인증 사용자가 소유한 후보 매물 한 건을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "후보 매물 조회 성공",
                content = [Content(schema = Schema(implementation = HomePropertyEnvelope::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(
                responseCode = "404",
                description = "후보 매물을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
        ],
    )
    @GetMapping("/{propertyId}")
    fun get(
        authentication: Authentication,
        @Parameter(description = "후보 매물 ID", example = "42")
        @PathVariable propertyId: Long,
    ): HomePropertyEnvelope =
        HomePropertyEnvelope(toResponse(service.get(authentication.principal as Long, propertyId)))

    @Operation(summary = "후보 매물 등록", description = "계약 전 비교 분석에 사용할 후보 매물을 새로 등록합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "후보 매물 등록 성공",
                content = [Content(schema = Schema(implementation = HomePropertyEnvelope::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "요청 형식 또는 필드 값이 올바르지 않음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @OpenApiRequestBody(
            description = "등록할 후보 매물 정보",
            required = true,
            content = [Content(schema = Schema(implementation = HomePropertyCreateRequest::class))],
        )
        @Valid @RequestBody request: HomePropertyCreateRequest,
    ): HomePropertyEnvelope =
        HomePropertyEnvelope(
            toResponse(
                service.create(
                    authentication.principal as Long,
                    HomePropertyCreateCommand(
                        request.address,
                        request.depositAmount,
                        request.buildingType,
                        request.landlordName,
                        request.plannedContractDate,
                    ),
                ),
            ),
        )

    @Operation(
        summary = "후보 매물 부분 수정",
        description = "JSON Merge Patch 형식으로 주소, 보증금 또는 임대인 이름을 부분 수정합니다. landlordName의 null은 값을 삭제합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "후보 매물 수정 성공",
                content = [Content(schema = Schema(implementation = HomePropertyEnvelope::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Merge Patch 형식 또는 필드 값이 올바르지 않음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(
                responseCode = "404",
                description = "후보 매물을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
        ],
    )
    @PatchMapping("/{propertyId}", consumes = ["application/merge-patch+json"])
    fun patch(
        authentication: Authentication,
        @Parameter(description = "후보 매물 ID", example = "42")
        @PathVariable propertyId: Long,
        @OpenApiRequestBody(
            description = "수정할 필드만 포함한 JSON Merge Patch 문서",
            required = true,
            content = [
                Content(
                    mediaType = "application/merge-patch+json",
                    schema = Schema(
                        type = "object",
                        example = """{"depositAmount":22000,"landlordName":null}""",
                    ),
                ),
            ],
        )
        @RequestBody document: JsonNode,
    ): HomePropertyEnvelope {
        return HomePropertyEnvelope(toResponse(service.patch(authentication.principal as Long, propertyId, document.toPatchCommand())))
    }

    private fun JsonNode.toPatchCommand(): HomePropertyPatchCommand {
        if (!isObject || size() == 0) throw InvalidHomePropertyRequestException()
        val address = get("address")?.let { node ->
            if (!node.isString || node.asString().trim().isEmpty()) throw InvalidHomePropertyRequestException()
            FieldPatch.Set(node.asString().trim())
        } ?: FieldPatch.Unchanged
        val landlord = if (!has("landlordName")) FieldPatch.Unchanged else {
            val node = get("landlordName")
            if (node.isNull) FieldPatch.Clear
            else if (node.isString && node.asString().trim().isNotEmpty()) FieldPatch.Set(node.asString().trim())
            else throw InvalidHomePropertyRequestException()
        }
        val deposit = get("depositAmount")?.let { node ->
            if (!node.isIntegralNumber || node.asLong() <= 0) throw InvalidHomePropertyRequestException()
            FieldPatch.Set(node.asLong())
        } ?: FieldPatch.Unchanged
        if (listOf("address", "landlordName", "depositAmount").none { has(it) }) throw InvalidHomePropertyRequestException()
        return HomePropertyPatchCommand(address = address, landlordName = landlord, depositAmount = deposit)
    }

    private fun toResponse(property: HomeProperty): HomePropertyResponse =
        HomePropertyResponse(
            id = requireNotNull(property.id),
            address = property.address,
            depositAmount = property.depositAmount,
            buildingType = property.buildingType,
            landlordName = property.landlordName,
            plannedContractDate = property.plannedContractDate,
        )
}
