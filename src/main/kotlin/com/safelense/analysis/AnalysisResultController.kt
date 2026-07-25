// 인증 사용자의 분석 이력 목록과 저장 결과 상세 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

import com.safelense.analysis.report.ContractDecisionReportService
import com.safelense.analysis.report.ContractDecisionReportView
import com.safelense.analysis.run.AnalysisRunNotFoundException
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
    private val contractReportService: ContractDecisionReportService,
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

    @Operation(
        summary = "분석 결과 상세 조회",
        description = "기존 결과를 기본 조회하며 새 계약 의사결정 리포트는 resultType=CONTRACT_DECISION으로 구분합니다.",
    )
    @GetMapping("/{analysisId}")
    fun get(
        authentication: Authentication,
        @PathVariable analysisId: String,
        @RequestParam(required = false) resultType: String?,
    ): Any =
        resolve(
            authentication.principal as Long,
            analysisId.toLongOrNull() ?: invalidRequest(),
            parseResultType(resultType),
        )

    @Operation(
        summary = "분석 PDF 리포트 다운로드",
        description = "새 계약 의사결정 PDF는 resultType=CONTRACT_DECISION으로 구분합니다.",
    )
    @GetMapping("/{analysisId}/report.pdf")
    fun report(
        authentication: Authentication,
        @PathVariable analysisId: String,
        @RequestParam(required = false) resultType: String?,
    ): ResponseEntity<ByteArray> {
        val id = analysisId.toLongOrNull() ?: invalidRequest()
        val userId = authentication.principal as Long
        val report = when (val result = resolve(userId, id, parseResultType(resultType))) {
            is AnalysisResultDetail -> reportService.create(result)
            is ContractDecisionReportView -> reportService.create(result)
            else -> error("Unsupported analysis result type")
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"safelense-analysis-$id.pdf\"")
            .body(report)
    }

    private fun resolve(userId: Long, analysisId: Long, resultType: AnalysisResultType?): Any =
        when (resultType) {
            AnalysisResultType.LEGACY ->
                service.find(userId, analysisId) ?: throw AnalysisResultNotFoundException()
            AnalysisResultType.CONTRACT_DECISION ->
                contractReportService.find(userId, analysisId) ?: throw AnalysisRunNotFoundException()
            null ->
                service.find(userId, analysisId)
                    ?: contractReportService.find(userId, analysisId)
                    ?: throw AnalysisResultNotFoundException()
        }

    private fun parseResultType(value: String?): AnalysisResultType? =
        value?.let {
            runCatching { AnalysisResultType.valueOf(it) }.getOrElse { invalidRequest() }
        }

    private fun invalidRequest(): Nothing = throw InvalidAnalysisResultRequestException()
}

private enum class AnalysisResultType {
    LEGACY,
    CONTRACT_DECISION,
}
