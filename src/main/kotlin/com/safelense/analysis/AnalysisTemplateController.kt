// 계약 단계별 서류와 체크리스트 템플릿 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/analysis-templates")
class AnalysisTemplateController(
    private val catalog: AnalysisTemplateCatalog,
) {
    @GetMapping("/{stage}")
    fun get(@PathVariable stage: String): AnalysisTemplate =
        catalog.get(catalog.parse(stage))
}
