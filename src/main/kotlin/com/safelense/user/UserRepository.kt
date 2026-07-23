// 카카오 회원번호로 사용자를 조회하는 JPA 저장소
package com.safelense.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByKakaoId(kakaoId: Long): User?
}
