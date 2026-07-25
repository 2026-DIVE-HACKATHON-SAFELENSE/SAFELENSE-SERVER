// S3 등기부 저장소가 KMS 암호화와 비공개 객체 키를 사용하는지 검증하는 테스트
package com.safelense.document

import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.ServerSideEncryption

class S3RegistryDocumentStorageTests {
    private val client = mock(S3Client::class.java)
    private val properties = RegistryDocumentProperties(
        bucket = "registry-bucket",
        kmsKeyId = "kms-key",
        retention = Duration.ofDays(30),
    )
    private val storage = S3RegistryDocumentStorage(client, properties)

    @Test
    fun `uploads a private KMS encrypted object with audit metadata`() {
        val expiresAt = Instant.parse("2026-08-25T00:00:00Z")

        val stored = storage.put("pdf".toByteArray(), "application/pdf", "a".repeat(64), expiresAt)

        val request = ArgumentCaptor.forClass(PutObjectRequest::class.java)
        verify(client).putObject(request.capture(), any(RequestBody::class.java))
        assertThat(request.value.bucket()).isEqualTo("registry-bucket")
        assertThat(request.value.key()).startsWith("private/registry/").endsWith(".pdf")
        assertThat(request.value.serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS)
        assertThat(request.value.ssekmsKeyId()).isEqualTo("kms-key")
        assertThat(request.value.metadata())
            .containsEntry("sha256", "a".repeat(64))
            .containsEntry("expires-at", expiresAt.toString())
        assertThat(stored.storageKey).isEqualTo(request.value.key())
        assertThat(stored.expiresAt).isEqualTo(expiresAt)
    }

    @Test
    fun `deletes an object from the configured private bucket`() {
        storage.delete("private/registry/document.pdf")

        val request = ArgumentCaptor.forClass(DeleteObjectRequest::class.java)
        verify(client).deleteObject(request.capture())
        assertThat(request.value.bucket()).isEqualTo("registry-bucket")
        assertThat(request.value.key()).isEqualTo("private/registry/document.pdf")
    }
}
