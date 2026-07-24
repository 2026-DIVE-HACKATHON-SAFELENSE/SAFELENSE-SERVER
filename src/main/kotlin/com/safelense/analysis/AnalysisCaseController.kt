// 인증 사용자의 분석 케이스 생성과 입력 상태 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Schema(description = "분석 케이스 생성 요청")
data class AnalysisCaseCreateRequest(
    @field:NotBlank
    @field:Schema(description = "계약 단계", example = "BEFORE_CONTRACT", allowableValues = ["BEFORE_CONTRACT", "DURING_CONTRACT", "AFTER_CONTRACT"])
    val stage: String,
    @field:Positive
    @field:Schema(description = "분석할 내 집 정보 ID", example = "42")
    val propertyId: Long,
)

@Tag(name = "분석 케이스", description = "전세 계약 단계별 분석 케이스를 생성하고 입력 상태를 조회합니다.")
@RestController
@RequestMapping("/api/v1/analysis-cases")
class AnalysisCaseController(
    private val service: AnalysisCaseService,
    private val catalog: AnalysisTemplateCatalog,
) {
    @Operation(summary = "분석 케이스 생성", description = "등록한 내 집과 계약 단계를 연결한 분석 케이스를 생성합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: AnalysisCaseCreateRequest,
    ): AnalysisCaseCreated =
        service.create(
            authentication.principal as Long,
            AnalysisCaseCreateCommand(catalog.parse(request.stage), request.propertyId),
        )

    @Operation(summary = "분석 케이스 상세 조회", description = "서류 슬롯, 업로드 상태와 체크리스트 답변을 조회합니다.")
    @GetMapping("/{caseId}")
    fun get(
        authentication: Authentication,
        @PathVariable caseId: Long,
    ): AnalysisCaseView =
        service.get(authentication.principal as Long, caseId)
}
