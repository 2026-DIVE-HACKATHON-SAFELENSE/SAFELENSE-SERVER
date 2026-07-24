// 인증 사용자 프로필 조회와 온보딩 상태 변경을 처리하는 서비스
package com.safelense.user

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class UserNotFoundException : RuntimeException()

@Schema(description = "현재 인증 사용자의 프로필")
data class UserView(
    @field:Schema(description = "사용자 ID", example = "1")
    val id: Long,
    @field:Schema(description = "카카오 프로필 닉네임", example = "세이프렌즈")
    val nickname: String,
    @field:Schema(description = "카카오 프로필 이미지 주소")
    val profileImageUrl: String?,
    @field:Schema(description = "온보딩 완료 여부")
    val onboardingCompleted: Boolean,
)

@Service
class UserService(
    private val repository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun get(userId: Long): UserView =
        repository.findById(userId)
            .orElseThrow { UserNotFoundException() }
            .toView()

    @Transactional
    fun updateOnboarding(userId: Long, onboardingCompleted: Boolean): UserView {
        val user = repository.findById(userId)
            .orElseThrow { UserNotFoundException() }
        user.onboardingCompleted = onboardingCompleted
        return user.toView()
    }

    private fun User.toView(): UserView =
        UserView(
            id = requireNotNull(id),
            nickname = nickname,
            profileImageUrl = profileImageUrl,
            onboardingCompleted = onboardingCompleted,
        )
}
