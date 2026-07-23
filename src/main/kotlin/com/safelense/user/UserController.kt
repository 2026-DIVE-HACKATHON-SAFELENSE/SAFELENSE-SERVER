// 인증 사용자의 프로필 조회와 온보딩 상태 변경 API를 제공하는 컨트롤러
package com.safelense.user

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class OnboardingUpdateRequest(
    @field:NotNull
    val onboardingCompleted: Boolean?,
)

@RestController
@RequestMapping("/api/v1/me")
class UserController(
    private val service: UserService,
) {
    @GetMapping
    fun get(authentication: Authentication): UserView =
        service.get(authentication.principal as Long)

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
