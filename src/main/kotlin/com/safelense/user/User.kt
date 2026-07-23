// 카카오 로그인 사용자의 최소 프로필을 저장하는 JPA 엔티티
package com.safelense.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "kakao_id", nullable = false, unique = true, updatable = false)
    var kakaoId: Long,
    @Column(nullable = false)
    var nickname: String,
    @Column(name = "profile_image_url")
    var profileImageUrl: String? = null,
    @Column(name = "onboarding_completed", nullable = false)
    var onboardingCompleted: Boolean = false,
)
