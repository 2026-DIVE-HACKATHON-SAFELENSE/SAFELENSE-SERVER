// 분석 체크리스트 답변 전체 교체 HTTP 요청을 처리하는 컨트롤러
package com.safelense.analysis

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AnalysisChecklistAnswerRequest(
    @field:NotBlank
    val itemKey: String,
    val checked: Boolean,
)

data class AnalysisChecklistReplaceRequest(
    @field:Valid
    val answers: List<AnalysisChecklistAnswerRequest>,
)

data class AnalysisChecklistEnvelope(
    val answers: List<AnalysisChecklistAnswerView>,
)

@RestController
@RequestMapping("/api/v1/analysis-cases/{caseId}/checklist")
class AnalysisChecklistController(
    private val service: AnalysisChecklistService,
) {
    @PutMapping
    fun replace(
        authentication: Authentication,
        @PathVariable caseId: Long,
        @Valid @RequestBody request: AnalysisChecklistReplaceRequest,
    ): AnalysisChecklistEnvelope =
        AnalysisChecklistEnvelope(
            service.replace(
                authentication.principal as Long,
                caseId,
                request.answers.map { AnalysisChecklistAnswerCommand(it.itemKey, it.checked) },
            ),
        )
}
