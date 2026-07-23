// 분석 결과 저장소의 사용자 격리 조회 계약을 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnalysisResultRepositoryContractTests {
    @Test
    fun `repository exposes owned history and detail queries`() {
        val methodNames = AnalysisResultRepository::class.java.methods.map { it.name }

        assertThat(methodNames).contains("findByUserIdWithCursor")
        assertThat(methodNames).contains("findByIdAndUserId")
        assertThat(methodNames).contains("findByCaseId")
        assertThat(methodNames).contains("existsByCaseId")
    }
}
