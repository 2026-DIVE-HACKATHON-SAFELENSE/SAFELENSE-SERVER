// 인증 사용자의 계약 전 분석 실행 생성과 상태 조회를 처리하는 서비스
package com.safelense.analysis.run

import com.safelense.property.HomePropertyNotFoundException
import com.safelense.property.HomePropertyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class InvalidAnalysisRunRequestException : RuntimeException()

class AnalysisRunNotFoundException : RuntimeException()

data class AnalysisRunView(
    val id: Long,
    val propertyId: Long,
    val status: AnalysisRunStatus,
    val dataMode: AnalysisDataMode,
    val forceRefresh: Boolean,
    val failureCode: String?,
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

    private fun AnalysisRun.toView() =
        AnalysisRunView(
            id = requireNotNull(id),
            propertyId = propertyId,
            status = status,
            dataMode = dataMode,
            forceRefresh = forceRefresh,
            failureCode = failureCode,
        )
}
