// 버전형 규칙으로 구조화된 임대차 위험 신호를 점수와 근거로 변환하는 엔진
package com.safelense.analysis

import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.property.HomeProperty
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

const val ANALYSIS_RULE_VERSION = "dive-2026-v1"

enum class SeniorRightStatus {
    NONE,
    MORTGAGE,
    PRIOR_TENANT,
    SEIZURE,
    UNKNOWN,
}

enum class DepositGuaranteeStatus {
    ENROLLED,
    NOT_ENROLLED,
    UNKNOWN,
}

enum class OwnershipStatus {
    MATCHED,
    MISMATCHED,
    UNKNOWN,
}

enum class SeizureOrAuctionStatus {
    NONE,
    SEIZURE,
    AUCTION,
    UNKNOWN,
}

data class AnalysisRiskInput(
    val stage: AnalysisStage,
    val depositAmountManwon: Long,
    val estimatedPropertyValueManwon: Long? = null,
    val seniorClaimAmountManwon: Long? = null,
    val seniorRightStatus: SeniorRightStatus = SeniorRightStatus.UNKNOWN,
    val depositGuaranteeStatus: DepositGuaranteeStatus = DepositGuaranteeStatus.UNKNOWN,
    val ownershipStatus: OwnershipStatus = OwnershipStatus.UNKNOWN,
    val seizureOrAuctionStatus: SeizureOrAuctionStatus = SeizureOrAuctionStatus.UNKNOWN,
    val checklistAnswers: Map<String, Boolean> = emptyMap(),
)

data class AnalysisRiskAssessment(
    val score: Int?,
    val grade: AnalysisRiskGrade,
    val confidence: Int,
    val summary: String,
    val findings: List<String>,
    val recommendations: List<String>,
    val ruleVersion: String,
)

@Component
class AnalysisRiskRuleEngine {
    fun assess(
        property: HomeProperty,
        evidence: List<CollectedEvidenceCommand>,
        objectMapper: ObjectMapper,
    ): AnalysisRiskAssessment {
        val available = evidence.filter { it.status == EvidenceStatus.AVAILABLE }
        val estimatedPropertyValue = available.firstOrNull { it.evidenceKey == "OFFICIAL_PRICE" }
            ?.valueJson
            ?.let(objectMapper::readTree)
            ?.get("amount")
            ?.takeIf { it.isIntegralNumber }
            ?.asLong()
        return assess(
            AnalysisRiskInput(
                stage = AnalysisStage.BEFORE_CONTRACT,
                depositAmountManwon = property.depositAmount,
                estimatedPropertyValueManwon = estimatedPropertyValue,
            ),
        )
    }

    fun assess(input: AnalysisRiskInput): AnalysisRiskAssessment {
        val findings = mutableListOf<String>()
        val recommendations = linkedSetOf<String>()
        val confidence = confidence(input)
        val effectiveRatio = effectiveRatio(input)

        val exposureScore = effectiveRatio?.let { ratio ->
            findings += "유효 담보비율은 ${ratio.setScale(1, RoundingMode.HALF_UP)}%입니다."
            when {
                ratio >= BigDecimal(90) -> 55
                ratio >= BigDecimal(80) -> 40
                ratio >= BigDecimal(70) -> 25
                ratio >= BigDecimal(60) -> 10
                else -> 0
            }
        }

        val rightsScore = rightsScore(input, findings, recommendations)
        val protectionScore =
            if (input.depositGuaranteeStatus == DepositGuaranteeStatus.NOT_ENROLLED) {
                findings += "보증금 반환보증에 가입하지 않은 상태입니다."
                recommendations += "보증금 반환보증 가입 가능 여부를 확인하세요."
                15
            } else {
                0
            }
        val procedureScore = procedureScore(input, findings, recommendations)
        val hardHighRisk =
            input.ownershipStatus == OwnershipStatus.MISMATCHED ||
                input.seniorRightStatus == SeniorRightStatus.SEIZURE ||
                input.seizureOrAuctionStatus == SeizureOrAuctionStatus.SEIZURE ||
                input.seizureOrAuctionStatus == SeizureOrAuctionStatus.AUCTION

        val hasScoreEvidence =
            exposureScore != null || rightsScore > 0 || protectionScore > 0 || procedureScore > 0
        val score =
            if (hasScoreEvidence) {
                ((exposureScore ?: 0) + rightsScore + protectionScore + procedureScore).coerceAtMost(100)
            } else {
                null
            }
        val grade =
            when {
                hardHighRisk -> AnalysisRiskGrade.HIGH
                score == null || effectiveRatio == null || confidence < 60 -> AnalysisRiskGrade.UNKNOWN
                score >= 60 -> AnalysisRiskGrade.HIGH
                score >= 30 -> AnalysisRiskGrade.MEDIUM
                else -> AnalysisRiskGrade.LOW
            }

        if (effectiveRatio == null) {
            recommendations += "추정 주택가액과 선순위 채권 정보를 확인하세요."
        } else if (effectiveRatio >= BigDecimal(80)) {
            recommendations += "등기부등본의 선순위 채권과 주택가액을 다시 확인하세요."
        }
        if (grade == AnalysisRiskGrade.UNKNOWN) {
            recommendations += "누락된 위험 정보를 입력한 뒤 결과를 다시 확인하세요."
        }

        return AnalysisRiskAssessment(
            score = score,
            grade = grade,
            confidence = confidence,
            summary = summary(grade),
            findings = findings,
            recommendations = recommendations.toList(),
            ruleVersion = ANALYSIS_RULE_VERSION,
        )
    }

    private fun effectiveRatio(input: AnalysisRiskInput): BigDecimal? {
        val propertyValue = input.estimatedPropertyValueManwon ?: return null
        val seniorClaim =
            when {
                input.seniorRightStatus == SeniorRightStatus.NONE -> 0L
                input.seniorClaimAmountManwon != null -> input.seniorClaimAmountManwon
                else -> return null
            }
        return BigDecimal.valueOf(input.depositAmountManwon)
            .add(BigDecimal.valueOf(seniorClaim))
            .multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(propertyValue), 4, RoundingMode.HALF_UP)
    }

    private fun confidence(input: AnalysisRiskInput): Int =
        (if (input.estimatedPropertyValueManwon != null) 35 else 0) +
            (if (input.seniorRightStatus != SeniorRightStatus.UNKNOWN) 20 else 0) +
            (
                if (
                    input.seniorRightStatus == SeniorRightStatus.NONE ||
                    input.seniorClaimAmountManwon != null
                ) {
                    10
                } else {
                    0
                }
            ) +
            (if (input.depositGuaranteeStatus != DepositGuaranteeStatus.UNKNOWN) 15 else 0) +
            (if (input.ownershipStatus != OwnershipStatus.UNKNOWN) 10 else 0) +
            (if (input.seizureOrAuctionStatus != SeizureOrAuctionStatus.UNKNOWN) 10 else 0)

    private fun rightsScore(
        input: AnalysisRiskInput,
        findings: MutableList<String>,
        recommendations: MutableSet<String>,
    ): Int {
        val scores = mutableListOf<Int>()
        when (input.seniorRightStatus) {
            SeniorRightStatus.MORTGAGE -> {
                findings += "선순위 근저당권이 존재합니다."
                recommendations += "등기부등본에서 채권최고액을 확인하세요."
                scores += 10
            }
            SeniorRightStatus.PRIOR_TENANT -> {
                findings += "선순위 임차인이 존재합니다."
                recommendations += "선순위 임차보증금과 배당 순위를 확인하세요."
                scores += 15
            }
            SeniorRightStatus.SEIZURE -> {
                findings += "압류 또는 가압류 권리가 존재합니다."
                recommendations += "계약 전에 법률 전문가에게 권리관계를 확인하세요."
                scores += 25
            }
            SeniorRightStatus.NONE,
            SeniorRightStatus.UNKNOWN,
            -> Unit
        }
        if (input.ownershipStatus == OwnershipStatus.MISMATCHED) {
            findings += "등기부상 소유자와 계약 상대방이 일치하지 않습니다."
            recommendations += "소유자 또는 적법한 대리권을 확인하기 전 계약을 진행하지 마세요."
            scores += 25
        }
        when (input.seizureOrAuctionStatus) {
            SeizureOrAuctionStatus.SEIZURE -> {
                findings += "압류 절차가 진행 중입니다."
                recommendations += "계약 전에 법률 전문가에게 권리관계를 확인하세요."
                scores += 25
            }
            SeizureOrAuctionStatus.AUCTION -> {
                findings += "경매 또는 공매 절차가 진행 중입니다."
                recommendations += "배당 가능성과 임차권 보호 순위를 확인하세요."
                scores += 25
            }
            SeizureOrAuctionStatus.NONE,
            SeizureOrAuctionStatus.UNKNOWN,
            -> Unit
        }
        return scores.maxOrNull() ?: 0
    }

    private fun procedureScore(
        input: AnalysisRiskInput,
        findings: MutableList<String>,
        recommendations: MutableSet<String>,
    ): Int {
        if (input.stage != AnalysisStage.AFTER_CONTRACT) return 0
        var score = 0
        if (input.checklistAnswers["RECEIVED_FIXED_DATE"] == false) {
            findings += "확정일자를 받지 않은 상태입니다."
            recommendations += "가능한 한 빨리 확정일자를 받으세요."
            score += 3
        }
        if (input.checklistAnswers["COMPLETED_MOVE_IN_REPORT"] == false) {
            findings += "전입신고를 완료하지 않은 상태입니다."
            recommendations += "가능한 한 빨리 전입신고를 완료하세요."
            score += 2
        }
        return score
    }

    private fun summary(grade: AnalysisRiskGrade): String =
        when (grade) {
            AnalysisRiskGrade.UNKNOWN -> "위험도를 판단하기 위한 입력 근거가 부족합니다."
            AnalysisRiskGrade.LOW -> "현재 입력에서 확인된 위험 신호가 낮습니다."
            AnalysisRiskGrade.MEDIUM -> "확인이 필요한 위험 신호가 있습니다."
            AnalysisRiskGrade.HIGH -> "즉시 확인이 필요한 높은 위험 신호가 있습니다."
        }
}
