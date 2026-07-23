// 인증 사용자의 분석 케이스 생성과 입력 상태 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

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

data class AnalysisCaseCreateRequest(
    @field:NotBlank
    val stage: String,
    @field:Positive
    val propertyId: Long,
)

@RestController
@RequestMapping("/api/v1/analysis-cases")
class AnalysisCaseController(
    private val service: AnalysisCaseService,
    private val catalog: AnalysisTemplateCatalog,
) {
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

    @GetMapping("/{caseId}")
    fun get(
        authentication: Authentication,
        @PathVariable caseId: Long,
    ): AnalysisCaseView =
        service.get(authentication.principal as Long, caseId)
}
