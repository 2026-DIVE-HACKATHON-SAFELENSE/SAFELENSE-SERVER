// 등기부 원본을 암호화된 객체 저장소에 보관하는 추상화
package com.safelense.document

import java.time.Instant

data class StoredRegistryDocument(
    val storageKey: String,
    val expiresAt: Instant,
)

interface RegistryDocumentStorage {
    fun put(content: ByteArray, contentType: String, sha256: String, expiresAt: Instant): StoredRegistryDocument

    fun delete(storageKey: String)
}
