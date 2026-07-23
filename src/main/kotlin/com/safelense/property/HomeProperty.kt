// 로그인 사용자의 현재 내 집 정보를 저장하는 JPA 엔티티
package com.safelense.property

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

enum class BuildingType {
    MULTI_FAMILY,
    APARTMENT,
    OFFICETEL,
    DETACHED_HOUSE,
}

@Entity
@Table(name = "home_properties")
class HomeProperty(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false, unique = true)
    var userId: Long,
    @Column(nullable = false, length = 500)
    var address: String,
    @Column(name = "deposit_amount", nullable = false)
    var depositAmount: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "building_type", nullable = false, length = 32)
    var buildingType: BuildingType,
    @Column(name = "landlord_name", length = 100)
    var landlordName: String? = null,
    @Column(name = "planned_contract_date")
    var plannedContractDate: LocalDate? = null,
)
