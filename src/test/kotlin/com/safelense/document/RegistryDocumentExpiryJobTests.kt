// 만료된 등기부 원본 정리 작업이 서비스 만료 처리를 실행하는지 검증하는 테스트
package com.safelense.document

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RegistryDocumentExpiryJobTests {
    @Test
    fun `expires due registry documents on schedule`() {
        val service = mock(RegistryDocumentService::class.java)
        val now = Instant.parse("2026-07-26T00:00:00Z")
        `when`(service.expireDue(now)).thenReturn(0)
        val job = RegistryDocumentExpiryJob(service, Clock.fixed(now, ZoneOffset.UTC))

        job.expire()

        verify(service).expireDue(now)
    }
}
