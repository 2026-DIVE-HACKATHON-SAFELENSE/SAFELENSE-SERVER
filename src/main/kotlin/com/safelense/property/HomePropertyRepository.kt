// 사용자 ID로 현재 내 집 정보를 조회하는 JPA 저장소
package com.safelense.property

import org.springframework.data.jpa.repository.JpaRepository

interface HomePropertyRepository : JpaRepository<HomeProperty, Long> {
    fun findByUserId(userId: Long): HomeProperty?
    fun findAllByUserIdOrderByIdDesc(userId: Long): List<HomeProperty>
    fun findByIdAndUserId(id: Long, userId: Long): HomeProperty?
}
