// 버전형 위험 규칙의 점수·등급·입력 충족률을 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnalysisRiskRuleEngineTests {
    private val engine = AnalysisRiskRuleEngine()

    @Test
    fun `returns unknown without score when no risk facts are available`() {
        val result = engine.assess(
            AnalysisRiskInput(
                stage = AnalysisStage.BEFORE_CONTRACT,
                depositAmountManwon = 25_000L,
            ),
        )

        assertThat(result.score).isNull()
        assertThat(result.grade).isEqualTo(AnalysisRiskGrade.UNKNOWN)
        assertThat(result.confidence).isZero()
        assertThat(result.ruleVersion).isEqualTo(ANALYSIS_RULE_VERSION)
    }

    @Test
    fun `applies effective collateral ratio score boundaries`() {
        val cases = listOf(
            59_000L to 0,
            60_000L to 10,
            70_000L to 25,
            80_000L to 40,
            90_000L to 55,
        )

        cases.forEach { (depositAmount, expectedScore) ->
            val result = engine.assess(completeInput(depositAmount))

            assertThat(result.score).isEqualTo(expectedScore)
        }
    }

    @Test
    fun `returns high for an explicit ownership mismatch without a property value`() {
        val result = engine.assess(
            AnalysisRiskInput(
                stage = AnalysisStage.BEFORE_CONTRACT,
                depositAmountManwon = 25_000L,
                ownershipStatus = OwnershipStatus.MISMATCHED,
            ),
        )

        assertThat(result.score).isEqualTo(25)
        assertThat(result.grade).isEqualTo(AnalysisRiskGrade.HIGH)
        assertThat(result.confidence).isEqualTo(10)
        assertThat(result.findings).anyMatch { it.contains("일치하지") }
    }

    @Test
    fun `keeps score and grade unknown when senior rights are not known`() {
        val result = engine.assess(
            AnalysisRiskInput(
                stage = AnalysisStage.BEFORE_CONTRACT,
                depositAmountManwon = 50_000L,
                estimatedPropertyValueManwon = 100_000L,
            ),
        )

        assertThat(result.score).isNull()
        assertThat(result.grade).isEqualTo(AnalysisRiskGrade.UNKNOWN)
        assertThat(result.confidence).isEqualTo(35)
    }

    @Test
    fun `adds after contract procedure risk only for explicit false answers`() {
        val result = engine.assess(
            completeInput(
                depositAmountManwon = 50_000L,
                stage = AnalysisStage.AFTER_CONTRACT,
                checklistAnswers = mapOf(
                    "RECEIVED_FIXED_DATE" to false,
                    "COMPLETED_MOVE_IN_REPORT" to false,
                ),
            ),
        )

        assertThat(result.score).isEqualTo(5)
        assertThat(result.grade).isEqualTo(AnalysisRiskGrade.LOW)
        assertThat(result.findings).anyMatch { it.contains("확정일자") }
        assertThat(result.findings).anyMatch { it.contains("전입신고") }
    }

    @Test
    fun `caps combined rights risk at twenty five points`() {
        val result = engine.assess(
            completeInput(
                depositAmountManwon = 50_000L,
                seniorRightStatus = SeniorRightStatus.SEIZURE,
                ownershipStatus = OwnershipStatus.MISMATCHED,
                seizureOrAuctionStatus = SeizureOrAuctionStatus.AUCTION,
            ),
        )

        assertThat(result.score).isEqualTo(25)
        assertThat(result.grade).isEqualTo(AnalysisRiskGrade.HIGH)
    }

    private fun completeInput(
        depositAmountManwon: Long,
        stage: AnalysisStage = AnalysisStage.BEFORE_CONTRACT,
        seniorRightStatus: SeniorRightStatus = SeniorRightStatus.NONE,
        ownershipStatus: OwnershipStatus = OwnershipStatus.MATCHED,
        seizureOrAuctionStatus: SeizureOrAuctionStatus = SeizureOrAuctionStatus.NONE,
        checklistAnswers: Map<String, Boolean> = emptyMap(),
    ): AnalysisRiskInput =
        AnalysisRiskInput(
            stage = stage,
            depositAmountManwon = depositAmountManwon,
            estimatedPropertyValueManwon = 100_000L,
            seniorClaimAmountManwon = 0L,
            seniorRightStatus = seniorRightStatus,
            depositGuaranteeStatus = DepositGuaranteeStatus.ENROLLED,
            ownershipStatus = ownershipStatus,
            seizureOrAuctionStatus = seizureOrAuctionStatus,
            checklistAnswers = checklistAnswers,
        )
}
