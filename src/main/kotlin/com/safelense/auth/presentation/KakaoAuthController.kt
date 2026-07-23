// 카카오 인가 코드로 서비스 JWT를 발급하는 HTTP 컨트롤러
package com.safelense.auth.presentation

import com.safelense.auth.application.KakaoLoginService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class KakaoLoginRequest(
    @field:NotBlank val authorizationCode: String,
    @field:NotBlank val redirectUri: String,
)

class KakaoLoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    private val newUser: Boolean,
) {
    fun getIsNewUser(): Boolean = newUser
}

@RestController
@RequestMapping("/api/v1/auth")
class KakaoAuthController(
    private val kakaoLoginService: KakaoLoginService,
) {
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
}
