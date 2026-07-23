// 인증 요청의 검증 오류와 카카오 연동 오류를 HTTP 응답으로 변환하는 처리기
package com.safelense.auth.presentation

import com.safelense.auth.kakao.KakaoApiUnavailableException
import com.safelense.auth.kakao.KakaoAuthenticationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiError(
    val code: String,
    val message: String,
)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationFailure(): ResponseEntity<ApiError> = error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid.")

    @ExceptionHandler(KakaoAuthenticationException::class)
    fun handleKakaoAuthenticationFailure(): ResponseEntity<ApiError> =
        error(HttpStatus.UNAUTHORIZED, "KAKAO_AUTHENTICATION_FAILED", "Kakao authorization code is invalid.")

    @ExceptionHandler(KakaoApiUnavailableException::class)
    fun handleKakaoApiUnavailable(): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_GATEWAY, "KAKAO_API_UNAVAILABLE", "Kakao API is unavailable.")

    private fun error(status: HttpStatus, code: String, message: String): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(ApiError(code, message))
}
