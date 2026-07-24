// 인증 사용자의 분석 이력 목록과 저장 결과 상세 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "분석 결과", description = "저장된 위험 분석 이력, 상세 결과와 PDF 리포트를 조회합니다.")
@RestController
@RequestMapping("/api/v1/analyses")
class AnalysisResultController(
    private val service: AnalysisResultService,
    private val reportService: AnalysisReportService,
) {
    @Operation(summary = "분석 이력 조회", description = "커서 기반 페이지네이션으로 분석 결과 이력을 조회합니다.")
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

    @Operation(summary = "분석 결과 상세 조회", description = "위험 점수, 근거와 권고 사항을 포함한 분석 결과를 조회합니다.")
    @GetMapping("/{analysisId}")
    fun get(
        authentication: Authentication,
        @PathVariable analysisId: String,
    ): AnalysisResultDetail =
        service.get(
            authentication.principal as Long,
            analysisId.toLongOrNull() ?: invalidRequest(),
        )

    @Operation(summary = "분석 PDF 리포트 다운로드", description = "저장된 분석 결과를 PDF 파일로 생성해 다운로드합니다.")
    @GetMapping("/{analysisId}/report.pdf")
    fun report(
        authentication: Authentication,
        @PathVariable analysisId: String,
    ): ResponseEntity<ByteArray> {
        val id = analysisId.toLongOrNull() ?: invalidRequest()
        val report = reportService.create(service.get(authentication.principal as Long, id))
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"safelense-analysis-$id.pdf\"")
            .body(report)
    }

    private fun invalidRequest(): Nothing = throw InvalidAnalysisResultRequestException()
}
