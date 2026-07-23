// 인증 사용자 프로필 조회와 온보딩 상태 변경을 처리하는 서비스
package com.safelense.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class UserNotFoundException : RuntimeException()

data class UserView(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
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
