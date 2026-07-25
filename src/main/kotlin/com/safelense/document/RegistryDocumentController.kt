// 후보 매물의 선택 등기부 원본 업로드와 즉시 삭제 API를 제공하는 컨트롤러
package com.safelense.document

import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

data class RegistryDocumentEnvelope(
    val document: RegistryDocumentView,
)

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/registry-documents")
class RegistryDocumentController(
    private val service: RegistryDocumentService,
) {
    @PostMapping(consumes = ["multipart/form-data"])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        authentication: Authentication,
        @PathVariable propertyId: Long,
        @RequestParam file: MultipartFile,
    ): RegistryDocumentEnvelope =
        RegistryDocumentEnvelope(service.upload(authentication.principal as Long, propertyId, file))

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        authentication: Authentication,
        @PathVariable propertyId: Long,
        @PathVariable documentId: Long,
    ) {
        service.delete(authentication.principal as Long, propertyId, documentId)
    }
}
