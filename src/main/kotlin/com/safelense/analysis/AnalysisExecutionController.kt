// 인증 사용자의 분석 케이스에 대해 위험 분석을 실행하는 HTTP API
package com.safelense.analysis

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

data class AnalysisExecutionRequest(
    @field:Positive
    val estimatedPropertyValueManwon: Long? = null,
    @field:PositiveOrZero
    val seniorClaimAmountManwon: Long? = null,
    val seniorRightStatus: SeniorRightStatus = SeniorRightStatus.UNKNOWN,
    val depositGuaranteeStatus: DepositGuaranteeStatus = DepositGuaranteeStatus.UNKNOWN,
    val ownershipStatus: OwnershipStatus = OwnershipStatus.UNKNOWN,
    val seizureOrAuctionStatus: SeizureOrAuctionStatus = SeizureOrAuctionStatus.UNKNOWN,
)

@RestController
@RequestMapping("/api/v1/analysis-cases")
class AnalysisExecutionController(
    private val service: AnalysisExecutionService,
) {
    @PostMapping("/{caseId}/analyze")
    fun analyze(
        authentication: Authentication,
        @PathVariable caseId: String,
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
