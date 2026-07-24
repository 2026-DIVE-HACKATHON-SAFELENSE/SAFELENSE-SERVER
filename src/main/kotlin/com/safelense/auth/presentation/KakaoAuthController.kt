// 카카오 인가 코드로 서비스 JWT를 발급하는 HTTP 컨트롤러
package com.safelense.auth.presentation

import com.safelense.auth.application.KakaoLoginService
import com.safelense.auth.application.LogoutService
import com.safelense.auth.application.TokenRefreshService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Schema(description = "카카오 로그인 요청")
data class KakaoLoginRequest(
    @field:NotBlank
    @field:Schema(description = "카카오에서 전달받은 인가 코드", example = "authorization-code")
    val authorizationCode: String,
    @field:NotBlank
    @field:Schema(description = "카카오 로그인 후 돌아올 프런트 콜백 주소", example = "https://safelense.site/auth/kakao/callback")
    val redirectUri: String,
)

@Schema(description = "토큰 재발급 요청")
data class TokenRefreshRequest(
    @field:NotBlank
    @field:Schema(description = "로그인 또는 이전 재발급에서 받은 refreshToken", example = "eyJhbGciOiJIUzI1NiJ9.refresh-token")
    val refreshToken: String,
)

@Schema(description = "카카오 로그인 성공 시 발급되는 JWT 토큰 정보")
class KakaoLoginResponse(
    @field:Schema(description = "API 요청에 사용할 JWT accessToken")
    val accessToken: String,
    @field:Schema(description = "토큰 재발급에 사용할 JWT refreshToken")
    val refreshToken: String,
    @field:Schema(description = "Authorization 헤더의 토큰 유형", example = "Bearer")
    val tokenType: String = "Bearer",
    @field:Schema(description = "accessToken 만료까지 남은 초", example = "1800")
    val expiresIn: Long,
    private val newUser: Boolean,
) {
    fun getIsNewUser(): Boolean = newUser
}

@Schema(description = "토큰 재발급 성공 시 발급되는 JWT 토큰 정보")
data class TokenRefreshResponse(
    @field:Schema(description = "새 API 요청에 사용할 JWT accessToken")
    val accessToken: String,
    @field:Schema(description = "다음 재발급에 사용할 새 JWT refreshToken")
    val refreshToken: String,
    @field:Schema(description = "Authorization 헤더의 토큰 유형", example = "Bearer")
    val tokenType: String = "Bearer",
    @field:Schema(description = "accessToken 만료까지 남은 초", example = "1800")
    val expiresIn: Long,
)

@Tag(name = "인증", description = "카카오 로그인으로 JWT를 발급하고 토큰을 재발급하거나 로그아웃합니다.")
@RestController
@RequestMapping("/api/v1/auth")
class KakaoAuthController(
    private val kakaoLoginService: KakaoLoginService,
    private val tokenRefreshService: TokenRefreshService,
    private val logoutService: LogoutService,
) {
    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 SAFELENSE accessToken과 refreshToken을 발급합니다.")
    @PostMapping("/kakao")
    @ResponseStatus(HttpStatus.OK)
    fun login(@Valid @RequestBody request: KakaoLoginRequest): KakaoLoginResponse {
        val result = kakaoLoginService.login(request.authorizationCode, request.redirectUri)
        return KakaoLoginResponse(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            expiresIn = result.expiresIn,
            newUser = result.isNewUser,
        )
    }

    @Operation(summary = "토큰 재발급", description = "유효한 refreshToken으로 새 accessToken과 refreshToken을 발급합니다.")
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    fun refresh(@Valid @RequestBody request: TokenRefreshRequest): TokenRefreshResponse {
        val result = tokenRefreshService.refresh(request.refreshToken)
        return TokenRefreshResponse(result.accessToken, result.refreshToken, expiresIn = result.expiresIn)
    }

    @Operation(summary = "로그아웃", description = "현재 accessToken 사용자에게 연결된 refreshToken을 폐기합니다.")
    @PostMapping("/logout")
    fun logout(authentication: Authentication): ResponseEntity<Void> {
        logoutService.logout(authentication.principal as Long)
        return ResponseEntity.noContent().build()
    }
}
