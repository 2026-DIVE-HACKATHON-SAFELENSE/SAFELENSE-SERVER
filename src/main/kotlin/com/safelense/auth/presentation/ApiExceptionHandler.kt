// 인증 요청의 검증 오류와 카카오 연동 오류를 HTTP 응답으로 변환하는 처리기
package com.safelense.auth.presentation

import com.safelense.analysis.AnalysisCaseNotFoundException
import com.safelense.analysis.AnalysisAlreadyCompletedException
import com.safelense.analysis.AnalysisCaseLockedException
import com.safelense.analysis.AnalysisDocumentNotFoundException
import com.safelense.analysis.AnalysisDocumentTooLargeException
import com.safelense.analysis.AnalysisResultNotFoundException
import com.safelense.analysis.InvalidAnalysisExecutionRequestException
import com.safelense.analysis.InvalidAnalysisChecklistException
import com.safelense.analysis.InvalidAnalysisDocumentException
import com.safelense.analysis.InvalidAnalysisResultRequestException
import com.safelense.analysis.InvalidAnalysisStageException
import com.safelense.analysis.run.AnalysisRunNotFoundException
import com.safelense.analysis.run.InvalidAnalysisRunRequestException
import com.safelense.auth.kakao.KakaoApiUnavailableException
import com.safelense.auth.kakao.KakaoAuthenticationException
import com.safelense.auth.application.InvalidRefreshTokenException
import com.safelense.document.InvalidRegistryDocumentException
import com.safelense.document.RegistryDocumentExpiredException
import com.safelense.document.RegistryDocumentNotFoundException
import com.safelense.document.RegistryDocumentTooLargeException
import com.safelense.notification.InvalidNotificationRequestException
import com.safelense.notification.NotificationNotFoundException
import com.safelense.property.HomePropertyAlreadyExistsException
import com.safelense.property.HomePropertyNotFoundException
import com.safelense.property.InvalidHomePropertyRequestException
import com.safelense.user.UserNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

data class ApiError(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found.")

    @ExceptionHandler(InvalidAnalysisResultRequestException::class)
    fun handleInvalidAnalysisResultRequest(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid.")

    @ExceptionHandler(AnalysisResultNotFoundException::class)
    fun handleAnalysisResultNotFound(): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Analysis result was not found.")

    @ExceptionHandler(AnalysisRunNotFoundException::class)
    fun handleAnalysisRunNotFound(): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Analysis was not found.")

    @ExceptionHandler(InvalidAnalysisRunRequestException::class)
    fun handleInvalidAnalysisRunRequest(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid.")

    @ExceptionHandler(InvalidAnalysisExecutionRequestException::class)
    fun handleInvalidAnalysisExecutionRequest(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid.")

    @ExceptionHandler(AnalysisAlreadyCompletedException::class)
    fun handleAnalysisAlreadyCompleted(): ResponseEntity<ApiError> =
        error(HttpStatus.CONFLICT, "ANALYSIS_ALREADY_COMPLETED", "Analysis case was already analyzed.")

    @ExceptionHandler(AnalysisCaseLockedException::class)
    fun handleAnalysisCaseLocked(): ResponseEntity<ApiError> =
        error(HttpStatus.CONFLICT, "ANALYSIS_CASE_LOCKED", "Analysis case inputs are locked.")

    @ExceptionHandler(InvalidAnalysisChecklistException::class)
    fun handleInvalidAnalysisChecklist(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_CHECKLIST", "Checklist is invalid.")

    @ExceptionHandler(InvalidAnalysisDocumentException::class)
    fun handleInvalidAnalysisDocument(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT", "Document is invalid.")

    @ExceptionHandler(AnalysisDocumentTooLargeException::class, MaxUploadSizeExceededException::class)
    fun handleAnalysisDocumentTooLarge(): ResponseEntity<ApiError> =
        error(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TOO_LARGE", "Document is too large.")

    @ExceptionHandler(AnalysisDocumentNotFoundException::class)
    fun handleAnalysisDocumentNotFound(): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "ANALYSIS_DOCUMENT_NOT_FOUND", "Analysis document was not found.")

    @ExceptionHandler(AnalysisCaseNotFoundException::class)
    fun handleAnalysisCaseNotFound(): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "ANALYSIS_CASE_NOT_FOUND", "Analysis case was not found.")

    @ExceptionHandler(InvalidAnalysisStageException::class)
    fun handleInvalidAnalysisStage(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_STAGE", "Analysis stage is invalid.")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationFailure(): ResponseEntity<ApiError> = error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid.")

    @ExceptionHandler(InvalidHomePropertyRequestException::class, HttpMessageNotReadableException::class)
    fun handleInvalidRequest(): ResponseEntity<ApiError> = error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid.")

    @ExceptionHandler(HomePropertyAlreadyExistsException::class)
    fun handleHomePropertyAlreadyExists(): ResponseEntity<ApiError> =
        error(HttpStatus.CONFLICT, "PROPERTY_ALREADY_EXISTS", "Property already exists.")

    @ExceptionHandler(HomePropertyNotFoundException::class)
    fun handleHomePropertyNotFound(): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "PROPERTY_NOT_FOUND", "Property was not found.")

    @ExceptionHandler(InvalidRegistryDocumentException::class)
    fun handleInvalidRegistryDocument(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REGISTRY_DOCUMENT", "Registry document is invalid.")

    @ExceptionHandler(RegistryDocumentTooLargeException::class)
    fun handleRegistryDocumentTooLarge(): ResponseEntity<ApiError> =
        error(HttpStatus.PAYLOAD_TOO_LARGE, "REGISTRY_DOCUMENT_TOO_LARGE", "Registry document is too large.")

    @ExceptionHandler(RegistryDocumentNotFoundException::class)
    fun handleRegistryDocumentNotFound(): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "REGISTRY_DOCUMENT_NOT_FOUND", "Registry document was not found.")

    @ExceptionHandler(RegistryDocumentExpiredException::class)
    fun handleRegistryDocumentExpired(): ResponseEntity<ApiError> =
        error(HttpStatus.GONE, "DOCUMENT_EXPIRED", "Registry document has expired.")

    @ExceptionHandler(InvalidNotificationRequestException::class)
    fun handleInvalidNotificationRequest(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid.")

    @ExceptionHandler(NotificationNotFoundException::class)
    fun handleNotificationNotFound(): ResponseEntity<ApiError> =
        error(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification was not found.")

    @ExceptionHandler(KakaoAuthenticationException::class)
    fun handleKakaoAuthenticationFailure(): ResponseEntity<ApiError> =
        error(HttpStatus.UNAUTHORIZED, "KAKAO_AUTHENTICATION_FAILED", "Kakao authorization code is invalid.")

    @ExceptionHandler(KakaoApiUnavailableException::class)
    fun handleKakaoApiUnavailable(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_GATEWAY, "KAKAO_API_UNAVAILABLE", "Kakao API is unavailable.")

    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun handleInvalidRefreshToken(): ResponseEntity<ApiError> =
        error(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid.")

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        retryable: Boolean = false,
    ): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(ApiError(code, message, retryable))
}
