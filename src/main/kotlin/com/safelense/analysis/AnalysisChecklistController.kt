// 분석 체크리스트 답변 전체 교체 HTTP 요청을 처리하는 컨트롤러
package com.safelense.analysis

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Schema(description = "체크리스트 항목의 선택 상태")
data class AnalysisChecklistAnswerRequest(
    @field:NotBlank
    @field:Schema(description = "템플릿 체크리스트 항목 키", example = "VISITED_PROPERTY")
    val itemKey: String,
    @field:Schema(description = "항목 확인 여부", example = "true")
    val checked: Boolean,
)

@Schema(description = "체크리스트 답변 전체 저장 요청")
data class AnalysisChecklistReplaceRequest(
    @field:Valid
    @field:Schema(description = "저장할 체크리스트 답변 목록")
    val answers: List<AnalysisChecklistAnswerRequest>,
)

@Schema(description = "저장된 체크리스트 답변")
data class AnalysisChecklistEnvelope(
    @field:Schema(description = "저장된 체크리스트 답변 목록")
    val answers: List<AnalysisChecklistAnswerView>,
)

@Tag(name = "분석 체크리스트", description = "분석 케이스의 단계별 체크리스트 답변을 관리합니다.")
@RestController
@RequestMapping("/api/v1/analysis-cases/{caseId}/checklist")
class AnalysisChecklistController(
    private val service: AnalysisChecklistService,
) {
    @Operation(summary = "체크리스트 답변 전체 저장", description = "현재 케이스의 체크리스트 답변을 요청 본문으로 전체 교체합니다.")
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
