// 인증 사용자 프로필 조회와 온보딩 상태 변경을 검증하는 테스트
package com.safelense.user

import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UserServiceTests {
    private val repository = mock(UserRepository::class.java)
    private val service = UserService(repository)

    @Test
    fun `gets the authenticated user profile`() {
        `when`(repository.findById(7L)).thenReturn(Optional.of(user()))

        val result = service.get(7L)

        assertThat(result.id).isEqualTo(7L)
        assertThat(result.nickname).isEqualTo("세입자")
        assertThat(result.profileImageUrl).isEqualTo("https://example.com/profile.png")
        assertThat(result.onboardingCompleted).isFalse()
    }

    @Test
    fun `updates onboarding state`() {
        val user = user()
        `when`(repository.findById(7L)).thenReturn(Optional.of(user))

        val result = service.updateOnboarding(7L, true)

        assertThat(user.onboardingCompleted).isTrue()
        assertThat(result.onboardingCompleted).isTrue()
    }

    @Test
    fun `throws when the authenticated user does not exist`() {
        `when`(repository.findById(7L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.get(7L) }
            .isInstanceOf(UserNotFoundException::class.java)
        assertThatThrownBy { service.updateOnboarding(7L, true) }
            .isInstanceOf(UserNotFoundException::class.java)
    }

    private fun user() =
        User(
            id = 7L,
            kakaoId = 1234L,
            nickname = "세입자",
            profileImageUrl = "https://example.com/profile.png",
            onboardingCompleted = false,
        )
}
