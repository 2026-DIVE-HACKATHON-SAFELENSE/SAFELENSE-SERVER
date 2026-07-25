// 보존 기한이 지난 등기부 원본을 주기적으로 삭제하는 작업
package com.safelense.document

import java.time.Clock
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class RegistryDocumentExpiryJob(
    private val service: RegistryDocumentService,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Scheduled(fixedDelayString = "\${app.registry-document.expiry-scan-delay:PT1H}")
    fun expire() {
        service.expireDue(Instant.now(clock))
    }
}
