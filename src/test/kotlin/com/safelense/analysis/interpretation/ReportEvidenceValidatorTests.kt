// AI 해석이 존재하는 근거만 인용하고 근거 없는 단정을 만들지 않는지 검증하는 테스트
package com.safelense.analysis.interpretation

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ReportEvidenceValidatorTests {
    private val validator = ReportEvidenceValidator()

    @Test
    fun `rejects an evidence id that was not supplied to the model`() {
        val result = result(EvidenceBackedStatement("확인이 필요합니다.", listOf("missing")))

        assertThatThrownBy { validator.validate(result, setOf("evidence-1")) }
            .isInstanceOf(InvalidAiEvidenceException::class.java)
    }

    @Test
    fun `rejects a number that is absent from the cited evidence`() {
        val result = result(EvidenceBackedStatement("보증금 비율은 95%입니다.", listOf("evidence-1")))

        assertThatThrownBy {
            validator.validate(result, mapOf("evidence-1" to """{"ratio":0.82}"""))
        }.isInstanceOf(InvalidAiEvidenceException::class.java)
    }

    @Test
    fun `rejects a conclusive legal judgment`() {
        val result = result(EvidenceBackedStatement("이 계약은 법적으로 안전합니다.", listOf("evidence-1")))

        assertThatThrownBy {
            validator.validate(result, mapOf("evidence-1" to """{"ratio":0.82}"""))
        }.isInstanceOf(InvalidAiEvidenceException::class.java)
    }

    @Test
    fun `rejects using a consultation case as accident probability evidence`() {
        val result = result(EvidenceBackedStatement("사고 확률이 높습니다.", listOf("case-101")))

        assertThatThrownBy {
            validator.validate(result, mapOf("case-101" to "유사 대응 패턴"))
        }.isInstanceOf(InvalidAiEvidenceException::class.java)
    }

    private fun result(summary: EvidenceBackedStatement) =
        AiReportResult(
            summary = summary,
            residentialImpacts = emptyList(),
            actionGuide = emptyList(),
        )
}
