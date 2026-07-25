// 상담 조건의 구조화 일치도와 의미 점수를 고정 가중치로 계산하는 도구
package com.safelense.analysis.match

import org.springframework.stereotype.Component

data class ConsultationFeatures(
    val depositBand: String?,
    val housingTypes: Set<String>?,
    val seniorRights: String?,
    val guaranteeStatus: String?,
    val province: String?,
)

@Component
class ConsultationStructuredScorer {
    fun score(features: ConsultationFeatures, case: ConsultationCase): Double {
        var denominator = 0
        var matched = 0
        features.depositBand?.let {
            denominator += 30
            if (it == case.depositBand) matched += 30
        }
        features.housingTypes?.let {
            denominator += 20
            if (case.housingType in it) matched += 20
        }
        features.seniorRights?.let {
            denominator += 25
            if (it == case.seniorRights) matched += 25
        }
        features.guaranteeStatus?.let {
            denominator += 15
            if (it == case.guaranteeStatus) matched += 15
        }
        features.province?.let {
            denominator += 10
            if (it == case.province) matched += 10
        }
        return if (denominator == 0) 0.0 else matched.toDouble() / denominator
    }
}

fun combineConsultationScores(structured: Double, semantic: Double): Double =
    structured * 0.55 + semantic * 0.45
