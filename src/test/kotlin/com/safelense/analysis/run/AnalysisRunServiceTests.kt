// 계약 전 분석 실행의 생성·멱등성·소유권 상태 조회를 검증하는 테스트
package com.safelense.analysis.run

import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import com.safelense.property.HomePropertyRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AnalysisRunServiceTests {
    private val propertyRepository = mock(HomePropertyRepository::class.java)
    private val runRepository = mock(AnalysisRunRepository::class.java)
    private val service = AnalysisRunService(propertyRepository, runRepository)

    @Test
    fun `creates one queued live run for the same idempotency key`() {
        var stored: AnalysisRun? = null
        `when`(propertyRepository.findByIdAndUserId(2L, 1L)).thenReturn(property())
        `when`(runRepository.findByPropertyIdAndIdempotencyKey(2L, "run-1")).thenAnswer { stored }
        `when`(runRepository.saveAndFlush(any(AnalysisRun::class.java))).thenAnswer {
            (it.arguments[0] as AnalysisRun).apply { id = 3L }.also { run -> stored = run }
        }

        val first = service.create(1L, 2L, "run-1", false)
        val repeated = service.create(1L, 2L, "run-1", false)

        assertThat(first.id).isEqualTo(3L)
        assertThat(first.status).isEqualTo(AnalysisRunStatus.QUEUED)
        assertThat(first.dataMode).isEqualTo(AnalysisDataMode.LIVE)
        assertThat(repeated.id).isEqualTo(first.id)
        verify(runRepository, times(1)).saveAndFlush(any(AnalysisRun::class.java))
    }

    @Test
    fun `hides a run that does not belong to the authenticated user`() {
        `when`(runRepository.findByIdAndUserId(3L, 1L)).thenReturn(null)

        assertThatThrownBy { service.status(1L, 3L) }
            .isInstanceOf(AnalysisRunNotFoundException::class.java)
    }

    @Test
    fun `lists owned property runs and exposes only public retry information`() {
        `when`(propertyRepository.findByIdAndUserId(2L, 1L)).thenReturn(property())
        `when`(runRepository.findAllByPropertyIdAndUserIdOrderByIdDesc(2L, 1L)).thenReturn(
            listOf(
                AnalysisRun(
                    id = 4L,
                    propertyId = 2L,
                    userId = 1L,
                    status = AnalysisRunStatus.FAILED,
                    dataMode = AnalysisDataMode.DEMO,
                    idempotencyKey = "run-2",
                    forceRefresh = true,
                    failureCode = "provider timeout at s3://private-bucket/document.pdf",
                ),
                AnalysisRun(
                    id = 3L,
                    propertyId = 2L,
                    userId = 1L,
                    status = AnalysisRunStatus.COMPLETED,
                    dataMode = AnalysisDataMode.DEMO,
                    idempotencyKey = "run-1",
                    forceRefresh = false,
                ),
            ),
        )

        val history = service.history(1L, 2L)

        assertThat(history.analyses.map { it.id }).containsExactly(4L, 3L)
        assertThat(history.analyses.first().retryable).isTrue()
        assertThat(history.analyses.first().failureCode).isEqualTo("ANALYSIS_EXECUTION_FAILED")
        assertThat(history.analyses.last().retryable).isFalse()
    }

    private fun property() =
        HomeProperty(
            id = 2L,
            userId = 1L,
            address = "서울특별시 중구 세종대로 110",
            depositAmount = 20000,
            buildingType = BuildingType.APARTMENT,
        )
}
