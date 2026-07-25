// non-web Flyway 사전 점검에서 분석 스케줄 워커가 생성되지 않는지 검증하는 테스트
package com.safelense.analysis.run

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication

class AnalysisRunWorkerConfigurationTests {
    @Test
    fun `worker is active only in a servlet web application`() {
        val condition = AnalysisRunWorker::class.java.getAnnotation(ConditionalOnWebApplication::class.java)

        assertThat(condition).isNotNull()
        assertThat(condition.type).isEqualTo(ConditionalOnWebApplication.Type.SERVLET)
    }
}
