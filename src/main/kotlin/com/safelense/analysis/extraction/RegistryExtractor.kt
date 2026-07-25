// 선택 등기부의 추출 가능 여부를 실행 근거로 변환하는 교체 가능한 경계
package com.safelense.analysis.extraction

import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.document.RegistryDocument
import com.safelense.document.RegistryExtractionStatus
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

fun interface RegistryExtractor {
    fun extract(document: RegistryDocument?): List<CollectedEvidenceCommand>
}

@Component
class DemoRegistryExtractor(
    private val clock: Clock = Clock.systemUTC(),
) : RegistryExtractor {
    override fun extract(document: RegistryDocument?): List<CollectedEvidenceCommand> {
        val status = when (document?.extractionStatus) {
            null -> EvidenceStatus.NOT_AVAILABLE
            RegistryExtractionStatus.COMPLETED -> EvidenceStatus.AVAILABLE
            RegistryExtractionStatus.PENDING,
            RegistryExtractionStatus.FAILED,
            RegistryExtractionStatus.EXPIRED,
            -> EvidenceStatus.UNAVAILABLE
        }
        return listOf(
            CollectedEvidenceCommand(
                evidenceKey = "REGISTRY_DOCUMENT",
                valueJson = if (status == EvidenceStatus.AVAILABLE) """{"extracted":true}""" else null,
                source = "USER_DOCUMENT",
                sourceIdentifier = document?.sha256,
                asOf = null,
                collectedAt = Instant.now(clock),
                confidence = if (status == EvidenceStatus.AVAILABLE) 100 else 0,
                status = status,
            ),
        )
    }
}
