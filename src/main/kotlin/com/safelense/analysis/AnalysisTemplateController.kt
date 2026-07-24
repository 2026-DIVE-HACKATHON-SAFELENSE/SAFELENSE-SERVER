// 계약 단계별 서류와 체크리스트 템플릿 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "분석 템플릿", description = "계약 단계에 맞는 필수 서류와 체크리스트 항목을 제공합니다.")
@RestController
@RequestMapping("/api/v1/analysis-templates")
class AnalysisTemplateController(
    private val catalog: AnalysisTemplateCatalog,
) {
    @Operation(summary = "계약 단계 템플릿 조회", description = "계약 전, 계약 중, 계약 후 단계별 서류와 체크리스트 템플릿을 조회합니다.")
    @GetMapping("/{stage}")
    fun get(@PathVariable stage: String): AnalysisTemplate =
        catalog.get(catalog.parse(stage))
}
