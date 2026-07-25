// 데모 근거 키만 사용해 고정된 비식별 상담 패턴을 반환하는 매처
package com.safelense.analysis.match

import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.evidence.EvidenceStatus
import org.springframework.stereotype.Component

@Component
class DemoConsultationCaseMatcher : ConsultationCaseMatcher {
    override fun match(evidence: List<CollectedEvidenceCommand>): List<MatchedCase> {
        val availableKeys = evidence
            .filter { it.status == EvidenceStatus.AVAILABLE }
            .mapTo(mutableSetOf()) { it.evidenceKey }
        if ("JEONSE_RATIO" !in availableKeys) {
            return emptyList()
        }
        return listOf(
            MatchedCase(
                caseId = "DEMO-HUG-001",
                similarity = 0.82,
                pattern = "HIGH_DEPOSIT_RATIO",
                summary = "보증금 비율이 높은 계약에서 권리와 보증 가능 여부를 추가 확인한 비식별 상담 패턴입니다.",
            ),
        )
    }
}
