// 후보 매물의 계약 전 분석 실행 생성과 상태 조회 API를 제공하는 컨트롤러
package com.safelense.analysis.run

import java.net.URI
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

data class AnalysisRunCreateRequest(
    val forceRefresh: Boolean = false,
)

@RestController
class AnalysisRunController(
    private val service: AnalysisRunService,
) {
    @PostMapping("/api/v1/properties/{propertyId}/analyses")
    fun create(
        authentication: Authentication,
        @PathVariable propertyId: Long,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
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

    @GetMapping("/api/v1/analyses/{analysisId}/status")
    fun status(
        authentication: Authentication,
        @PathVariable analysisId: Long,
    ): AnalysisRunView = service.status(authentication.principal as Long, analysisId)
}
