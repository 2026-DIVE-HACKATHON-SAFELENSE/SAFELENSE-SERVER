// 후보 매물의 선택 등기부 원본 업로드와 즉시 삭제 API를 제공하는 컨트롤러
package com.safelense.document

import com.safelense.auth.presentation.ApiError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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

@Schema(description = "등기부 원본 업로드 응답")
data class RegistryDocumentEnvelope(
    @field:Schema(description = "저장된 등기부 원본 메타데이터")
    val document: RegistryDocumentView,
)

@Tag(name = "등기 문서", description = "후보 매물에 연결할 등기부 원본 PDF를 업로드하거나 삭제합니다.")
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/registry-documents")
class RegistryDocumentController(
    private val service: RegistryDocumentService,
) {
    @Operation(
        summary = "등기부 원본 업로드",
        description = "PDF 한 건을 암호화 저장합니다. 최대 크기는 10MiB이며 저장 원본은 30일 뒤 만료됩니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "등기부 원본 업로드 성공",
                content = [Content(schema = Schema(implementation = RegistryDocumentEnvelope::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "빈 파일이거나 PDF 형식이 아님",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(
                responseCode = "404",
                description = "후보 매물을 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
            ApiResponse(
                responseCode = "413",
                description = "파일이 10MiB 제한을 초과함",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
        ],
    )
    @PostMapping(consumes = ["multipart/form-data"])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        authentication: Authentication,
        @Parameter(description = "후보 매물 ID", example = "42")
        @PathVariable propertyId: Long,
        @Parameter(description = "PDF 형식의 등기부 원본", required = true)
        @RequestParam file: MultipartFile,
    ): RegistryDocumentEnvelope =
        RegistryDocumentEnvelope(service.upload(authentication.principal as Long, propertyId, file))

    @Operation(summary = "등기부 원본 삭제", description = "후보 매물에 저장된 등기부 원본을 즉시 삭제합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "등기부 원본 삭제 성공"),
            ApiResponse(responseCode = "401", description = "인증 실패"),
            ApiResponse(
                responseCode = "404",
                description = "후보 매물 또는 등기 문서를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
            ApiResponse(
                responseCode = "410",
                description = "등기 문서가 이미 만료됨",
                content = [Content(schema = Schema(implementation = ApiError::class))],
            ),
        ],
    )
    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        authentication: Authentication,
        @Parameter(description = "후보 매물 ID", example = "42")
        @PathVariable propertyId: Long,
        @Parameter(description = "등기 문서 ID", example = "7")
        @PathVariable documentId: Long,
    ) {
        service.delete(authentication.principal as Long, propertyId, documentId)
    }
}
