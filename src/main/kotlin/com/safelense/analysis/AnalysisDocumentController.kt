// 인증 사용자의 분석 서류 업로드·삭제 API를 제공하는 컨트롤러
package com.safelense.analysis

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/analysis-cases/{caseId}/documents")
class AnalysisDocumentController(
    private val service: AnalysisDocumentService,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        authentication: Authentication,
        @PathVariable caseId: Long,
        @RequestParam documentType: String,
        @RequestParam file: MultipartFile,
    ): AnalysisDocumentUploadResult =
        service.upload(authentication.principal as Long, caseId, documentType, file)

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        authentication: Authentication,
        @PathVariable caseId: Long,
        @PathVariable documentId: Long,
    ) {
        service.delete(authentication.principal as Long, caseId, documentId)
    }
}
