// 사용자 ID로 리프레시 토큰을 조회하고 삭제하는 JPA 저장소
package com.safelense.auth.token

import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByUserId(userId: Long): RefreshToken?

    fun deleteByUserId(userId: Long): Long
}
