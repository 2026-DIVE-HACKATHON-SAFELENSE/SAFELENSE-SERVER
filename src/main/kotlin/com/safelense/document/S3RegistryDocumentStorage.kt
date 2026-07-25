// 등기부 원본을 SSE-KMS가 적용된 비공개 S3 객체로 저장하고 삭제하는 어댑터
package com.safelense.document

import java.time.Instant
import java.util.UUID
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.ServerSideEncryption

@Component
class S3RegistryDocumentStorage(
    private val client: S3Client,
    private val properties: RegistryDocumentProperties,
) : RegistryDocumentStorage {
    override fun put(
        content: ByteArray,
        contentType: String,
        sha256: String,
        expiresAt: Instant,
    ): StoredRegistryDocument {
        val storageKey = "private/registry/${UUID.randomUUID()}.pdf"
        client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.bucket)
                .key(storageKey)
                .contentType(contentType)
                .contentLength(content.size.toLong())
                .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                .ssekmsKeyId(properties.kmsKeyId)
                .metadata(
                    mapOf(
                        "sha256" to sha256,
                        "expires-at" to expiresAt.toString(),
                    ),
                )
                .build(),
            RequestBody.fromBytes(content),
        )
        return StoredRegistryDocument(storageKey, expiresAt)
    }

    override fun delete(storageKey: String) {
        client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.bucket)
                .key(storageKey)
                .build(),
        )
    }
}

@Configuration(proxyBeanMethods = false)
class RegistryDocumentS3Config {
    @Bean
    fun registryDocumentS3Client(properties: RegistryDocumentProperties): S3Client =
        S3Client.builder()
            .region(Region.of(properties.region))
            .build()
}
