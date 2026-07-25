// 인증 사용자의 분석 이력 목록과 저장 결과 상세 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

import com.safelense.analysis.report.ContractDecisionReportService
import com.safelense.analysis.report.ContractDecisionReportView
import com.safelense.analysis.run.AnalysisRunNotFoundException
import com.safelense.auth.presentation.ApiError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "분석 이력 조회 성공",
                content = [Content(schema = Schema(implementation = AnalysisHistoryPage::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "커서, 페이지 크기 또는 계약 단계가 올바르지 않음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
        ],
    )
    @GetMapping
    fun list(
        authentication: Authentication,
        @Parameter(description = "다음 페이지 조회용 분석 결과 ID 커서", example = "100")
        @RequestParam(required = false) cursor: String?,
        @Parameter(description = "페이지 크기. 1부터 100까지", example = "20")
        @RequestParam(defaultValue = "20") size: String,
        @Parameter(
            description = "계약 단계 필터",
            example = "BEFORE_CONTRACT",
            schema = Schema(allowableValues = ["BEFORE_CONTRACT", "DURING_CONTRACT", "AFTER_CONTRACT"]),
        )
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "분석 결과 조회 성공",
                content = [
                    Content(
                        schema = Schema(
                            oneOf = [AnalysisResultDetail::class, ContractDecisionReportView::class],
                        ),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "분석 ID 또는 결과 형식이 올바르지 않음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(
                responseCode = "404",
                description = "분석 결과를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
        ],
    )
    @GetMapping("/{analysisId}")
    fun get(
        authentication: Authentication,
        @Parameter(description = "분석 결과 또는 실행 ID", example = "100")
        @PathVariable analysisId: String,
        @Parameter(
            description = "결과 형식 구분. 생략하면 기존 결과와 계약 의사결정 리포트를 순서대로 조회합니다.",
            example = "CONTRACT_DECISION",
            schema = Schema(allowableValues = ["LEGACY", "CONTRACT_DECISION"]),
        )
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "PDF 리포트 생성 성공",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_PDF_VALUE,
                        schema = Schema(type = "string", format = "binary"),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "분석 ID 또는 결과 형식이 올바르지 않음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(
                responseCode = "404",
                description = "분석 결과를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
        ],
    )
    @GetMapping("/{analysisId}/report.pdf")
    fun report(
        authentication: Authentication,
        @Parameter(description = "분석 결과 또는 실행 ID", example = "100")
        @PathVariable analysisId: String,
        @Parameter(
            description = "결과 형식 구분. 생략하면 기존 결과와 계약 의사결정 리포트를 순서대로 조회합니다.",
            example = "CONTRACT_DECISION",
            schema = Schema(allowableValues = ["LEGACY", "CONTRACT_DECISION"]),
        )
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
