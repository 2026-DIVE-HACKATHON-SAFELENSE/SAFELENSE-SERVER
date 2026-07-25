// 후보 매물의 계약 전 분석 실행 생성과 상태 조회 API를 제공하는 컨트롤러
package com.safelense.analysis.run

import com.safelense.auth.presentation.ApiError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import java.net.URI
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@Schema(description = "계약 전 분석 실행 요청")
data class AnalysisRunCreateRequest(
    @field:Schema(description = "캐시된 수집 결과를 무시하고 다시 수집할지 여부", example = "false")
    val forceRefresh: Boolean = false,
)

@Tag(name = "계약 전 분석 실행", description = "후보 매물의 비동기 계약 전 분석을 시작하고 상태와 이력을 조회합니다.")
@RestController
class AnalysisRunController(
    private val service: AnalysisRunService,
) {
    @Operation(
        summary = "계약 전 분석 시작",
        description = "분석 실행을 대기열에 등록합니다. 같은 후보 매물과 Idempotency-Key 요청은 기존 실행을 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "분석 실행 접수 성공",
                content = [Content(schema = Schema(implementation = AnalysisRunView::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "멱등 키 또는 요청 형식이 올바르지 않음",
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
    @PostMapping("/api/v1/properties/{propertyId}/analyses")
    fun create(
        authentication: Authentication,
        @Parameter(description = "후보 매물 ID", example = "42")
        @PathVariable propertyId: Long,
        @Parameter(description = "동일 분석 요청을 식별하는 멱등 키", required = true, example = "property-42-analysis-v1")
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @OpenApiRequestBody(
            description = "재수집 여부. 생략하면 forceRefresh=false로 처리합니다.",
            required = false,
            content = [Content(schema = Schema(implementation = AnalysisRunCreateRequest::class))],
        )
        @RequestBody(required = false) request: AnalysisRunCreateRequest?,
    ): ResponseEntity<AnalysisRunView> {
        val view = service.create(
            authentication.principal as Long,
            propertyId,
            idempotencyKey.orEmpty(),
            request?.forceRefresh ?: false,
        )
        return ResponseEntity
            .accepted()
            .location(URI.create("/api/v1/analyses/${view.id}/status"))
            .body(view)
    }

    @Operation(summary = "계약 전 분석 상태 조회", description = "분석 실행의 현재 단계, 데이터 모드와 재시도 가능 여부를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "분석 실행 상태 조회 성공",
                content = [Content(schema = Schema(implementation = AnalysisRunView::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(
                responseCode = "404",
                description = "분석 실행을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
        ],
    )
    @GetMapping("/api/v1/analyses/{analysisId}/status")
    fun status(
        authentication: Authentication,
        @Parameter(description = "분석 실행 ID", example = "100")
        @PathVariable analysisId: Long,
    ): AnalysisRunView = service.status(authentication.principal as Long, analysisId)

    @Operation(summary = "후보 매물 분석 이력 조회", description = "후보 매물에 대해 생성된 분석 실행을 최신 실행순으로 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "분석 실행 이력 조회 성공",
                content = [Content(schema = Schema(implementation = AnalysisRunHistoryView::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(
                responseCode = "404",
                description = "후보 매물을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
        ],
    )
    @GetMapping("/api/v1/properties/{propertyId}/analyses")
    fun history(
        authentication: Authentication,
        @Parameter(description = "후보 매물 ID", example = "42")
        @PathVariable propertyId: Long,
    ): AnalysisRunHistoryView = service.history(authentication.principal as Long, propertyId)
}
