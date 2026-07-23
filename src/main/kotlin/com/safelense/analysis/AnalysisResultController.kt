// 인증 사용자의 분석 이력 목록과 저장 결과 상세 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/analyses")
class AnalysisResultController(
    private val service: AnalysisResultService,
) {
    @GetMapping
    fun list(
        authentication: Authentication,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: String,
        @RequestParam(required = false) stage: String?,
    ): AnalysisHistoryPage =
        service.list(
            userId = authentication.principal as Long,
            cursor = cursor?.toLongOrNull() ?: if (cursor == null) null else invalidRequest(),
            size = size.toIntOrNull() ?: invalidRequest(),
            stage = stage?.let {
                runCatching { AnalysisStage.valueOf(it) }.getOrElse { invalidRequest() }
            },
        )

    @GetMapping("/{analysisId}")
    fun get(
        authentication: Authentication,
        @PathVariable analysisId: String,
    ): AnalysisResultDetail =
        service.get(
            authentication.principal as Long,
            analysisId.toLongOrNull() ?: invalidRequest(),
        )

    private fun invalidRequest(): Nothing = throw InvalidAnalysisResultRequestException()
}
