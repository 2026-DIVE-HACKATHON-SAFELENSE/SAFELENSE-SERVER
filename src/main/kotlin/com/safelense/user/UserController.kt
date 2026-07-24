// 인증 사용자의 프로필 조회와 온보딩 상태 변경 API를 제공하는 컨트롤러
package com.safelense.user

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Schema(description = "온보딩 완료 상태 변경 요청")
data class OnboardingUpdateRequest(
    @field:NotNull
    @field:Schema(description = "온보딩 완료 여부", example = "true")
    val onboardingCompleted: Boolean?,
)

@Tag(name = "사용자", description = "현재 인증 사용자의 프로필과 온보딩 상태를 관리합니다.")
@RestController
@RequestMapping("/api/v1/me")
class UserController(
    private val service: UserService,
) {
    @Operation(summary = "내 프로필 조회", description = "현재 accessToken에 연결된 사용자의 프로필과 온보딩 완료 상태를 조회합니다.")
    @GetMapping
    fun get(authentication: Authentication): UserView =
        service.get(authentication.principal as Long)

    @Operation(summary = "온보딩 완료 상태 변경", description = "현재 사용자의 온보딩 완료 여부를 변경합니다.")
    @PatchMapping("/onboarding")
    fun updateOnboarding(
        authentication: Authentication,
        @Valid @RequestBody request: OnboardingUpdateRequest,
    ): UserView =
        service.updateOnboarding(
            authentication.principal as Long,
            requireNotNull(request.onboardingCompleted),
        )
}
