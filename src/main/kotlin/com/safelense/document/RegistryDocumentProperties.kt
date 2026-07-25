// 등기부 원본 S3 보관 위치와 암호화·보존 기간 설정을 바인딩하는 속성
package com.safelense.document

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.registry-document")
data class RegistryDocumentProperties(
    val bucket: String,
    val kmsKeyId: String,
    val region: String = "ap-northeast-2",
    val retention: Duration = Duration.ofDays(30),
)
