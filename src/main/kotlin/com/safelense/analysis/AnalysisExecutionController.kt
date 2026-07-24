// 인증 사용자의 분석 케이스에 대해 위험 분석을 실행하는 HTTP API
package com.safelense.analysis

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Schema(description = "규칙형 위험 분석에 사용할 계약·권리 입력값")
data class AnalysisExecutionRequest(
    @field:Positive
    @field:Schema(description = "추정 주택가액. 단위는 만원", example = "30000")
    val estimatedPropertyValueManwon: Long? = null,
    @field:PositiveOrZero
    @field:Schema(description = "선순위 채권액. 단위는 만원", example = "5000")
    val seniorClaimAmountManwon: Long? = null,
    @field:Schema(description = "선순위 권리 상태", example = "MORTGAGE")
    val seniorRightStatus: SeniorRightStatus = SeniorRightStatus.UNKNOWN,
    @field:Schema(description = "전세보증금 반환보증 가입 상태", example = "ENROLLED")
    val depositGuaranteeStatus: DepositGuaranteeStatus = DepositGuaranteeStatus.UNKNOWN,
    @field:Schema(description = "임대인과 소유자 일치 상태", example = "MATCHED")
    val ownershipStatus: OwnershipStatus = OwnershipStatus.UNKNOWN,
    @field:Schema(description = "압류 또는 경매 진행 상태", example = "NONE")
    val seizureOrAuctionStatus: SeizureOrAuctionStatus = SeizureOrAuctionStatus.UNKNOWN,
)

@Tag(name = "위험 분석", description = "입력된 계약 정보를 기준으로 규칙형 전세 위험 분석을 실행합니다.")
@RestController
@RequestMapping("/api/v1/analysis-cases")
class AnalysisExecutionController(
    private val service: AnalysisExecutionService,
) {
    @Operation(summary = "위험 분석 실행", description = "같은 Idempotency-Key로 재요청하면 기존 결과를 반환합니다. 분석 완료 뒤에는 케이스 입력을 변경할 수 없습니다.")
    @PostMapping("/{caseId}/analyze")
    fun analyze(
        authentication: Authentication,
        @PathVariable caseId: String,
        @Parameter(description = "동일 요청을 안전하게 재시도하기 위한 멱등 키", example = "analysis-case-42-v1")
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: AnalysisExecutionRequest,
    ): ResponseEntity<AnalysisResultDetail> {
        val outcome = service.analyze(
            userId = authentication.principal as Long,
            caseId = caseId.toLongOrNull() ?: throw InvalidAnalysisExecutionRequestException(),
            idempotencyKey = idempotencyKey.orEmpty(),
            command = request.toCommand(),
        )
        return if (outcome.created) {
            ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/analyses/${outcome.result.id}"))
                .body(outcome.result)
        } else {
            ResponseEntity.ok(outcome.result)
        }
    }

    private fun AnalysisExecutionRequest.toCommand(): AnalysisExecutionCommand =
        AnalysisExecutionCommand(
            estimatedPropertyValueManwon = estimatedPropertyValueManwon,
            seniorClaimAmountManwon = seniorClaimAmountManwon,
            seniorRightStatus = seniorRightStatus,
            depositGuaranteeStatus = depositGuaranteeStatus,
            ownershipStatus = ownershipStatus,
            seizureOrAuctionStatus = seizureOrAuctionStatus,
        )
}
