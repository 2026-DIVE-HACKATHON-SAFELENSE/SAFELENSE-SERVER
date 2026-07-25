// 인증 사용자의 계약 전 분석 실행 생성과 상태 조회를 처리하는 서비스
package com.safelense.analysis.run

import com.safelense.property.HomePropertyNotFoundException
import com.safelense.property.HomePropertyRepository
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class InvalidAnalysisRunRequestException : RuntimeException()

class AnalysisRunNotFoundException : RuntimeException()

@Schema(description = "계약 전 분석 실행 상태")
data class AnalysisRunView(
    @field:Schema(description = "분석 실행 ID", example = "100")
    val id: Long,
    @field:Schema(description = "분석 대상 후보 매물 ID", example = "42")
    val propertyId: Long,
    @field:Schema(description = "현재 분석 실행 단계", example = "ANALYZING")
    val status: AnalysisRunStatus,
    @field:Schema(description = "수집 데이터 모드", example = "DEMO")
    val dataMode: AnalysisDataMode,
    @field:Schema(description = "캐시를 무시한 재수집 요청 여부", example = "false")
    val forceRefresh: Boolean,
    @field:Schema(description = "실패한 실행의 공개 오류 코드. 실패하지 않았으면 null", example = "ANALYSIS_EXECUTION_FAILED")
    val failureCode: String?,
    @field:Schema(description = "현재 실행을 새 멱등 키로 재시도할 수 있는지 여부", example = "false")
    val retryable: Boolean = status == AnalysisRunStatus.PARTIAL || status == AnalysisRunStatus.FAILED,
)

@Schema(description = "계약 전 분석 실행 이력")
data class AnalysisRunHistoryView(
    @field:Schema(description = "최신 실행순 분석 이력")
    val analyses: List<AnalysisRunView>,
)

@Service
class AnalysisRunService(
    private val propertyRepository: HomePropertyRepository,
    private val runRepository: AnalysisRunRepository,
) {
    @Transactional
    fun create(
        userId: Long,
        propertyId: Long,
        idempotencyKey: String,
        forceRefresh: Boolean,
    ): AnalysisRunView {
        if (idempotencyKey.isBlank() || idempotencyKey.length > 100) {
            throw InvalidAnalysisRunRequestException()
        }
        propertyRepository.findByIdAndUserId(propertyId, userId) ?: throw HomePropertyNotFoundException()
        runRepository.findByPropertyIdAndIdempotencyKey(propertyId, idempotencyKey)?.let {
            return it.toView()
        }
        return runRepository.saveAndFlush(
            AnalysisRun(
                propertyId = propertyId,
                userId = userId,
                status = AnalysisRunStatus.QUEUED,
                dataMode = AnalysisDataMode.DEMO,
                idempotencyKey = idempotencyKey,
                forceRefresh = forceRefresh,
            ),
        ).toView()
    }

    @Transactional(readOnly = true)
    fun status(userId: Long, analysisId: Long): AnalysisRunView =
        (runRepository.findByIdAndUserId(analysisId, userId) ?: throw AnalysisRunNotFoundException()).toView()

    @Transactional(readOnly = true)
    fun history(userId: Long, propertyId: Long): AnalysisRunHistoryView {
        propertyRepository.findByIdAndUserId(propertyId, userId) ?: throw HomePropertyNotFoundException()
        return AnalysisRunHistoryView(
            runRepository.findAllByPropertyIdAndUserIdOrderByIdDesc(propertyId, userId).map { it.toView() },
        )
    }

    private fun AnalysisRun.toView() =
        AnalysisRunView(
            id = requireNotNull(id),
            propertyId = propertyId,
            status = status,
            dataMode = dataMode,
            forceRefresh = forceRefresh,
            failureCode = if (status == AnalysisRunStatus.FAILED) "ANALYSIS_EXECUTION_FAILED" else null,
            retryable = status == AnalysisRunStatus.PARTIAL || status == AnalysisRunStatus.FAILED,
        )
}
