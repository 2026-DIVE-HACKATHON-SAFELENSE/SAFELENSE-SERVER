// 인증 사용자의 내 집 조회·최초 등록·부분 수정을 처리하는 서비스
package com.safelense.property

import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class HomePropertyCreateCommand(
    val address: String,
    val depositAmount: Long,
    val buildingType: BuildingType,
    val landlordName: String?,
    val plannedContractDate: LocalDate?,
)

sealed interface FieldPatch<out T> {
    data object Unchanged : FieldPatch<Nothing>

    data class Set<T>(val value: T) : FieldPatch<T>

    data object Clear : FieldPatch<Nothing>
}

data class HomePropertyPatchCommand(
    val address: FieldPatch<String> = FieldPatch.Unchanged,
    val depositAmount: FieldPatch<Long> = FieldPatch.Unchanged,
    val buildingType: FieldPatch<BuildingType> = FieldPatch.Unchanged,
    val landlordName: FieldPatch<String> = FieldPatch.Unchanged,
    val plannedContractDate: FieldPatch<LocalDate> = FieldPatch.Unchanged,
)

class HomePropertyAlreadyExistsException : RuntimeException()

class HomePropertyNotFoundException : RuntimeException()

class InvalidHomePropertyRequestException : RuntimeException()

@Service
class HomePropertyService(
    private val homePropertyRepository: HomePropertyRepository,
) {
    @Transactional(readOnly = true)
    fun get(userId: Long): HomeProperty? = homePropertyRepository.findByUserId(userId)

    @Transactional
    fun create(userId: Long, command: HomePropertyCreateCommand): HomeProperty {
        if (homePropertyRepository.findByUserId(userId) != null) {
            throw HomePropertyAlreadyExistsException()
        }

        return homePropertyRepository.save(
            HomeProperty(
                userId = userId,
                address = command.address.trim(),
                depositAmount = command.depositAmount,
                buildingType = command.buildingType,
                landlordName = command.landlordName.normalizeOptionalText(),
                plannedContractDate = command.plannedContractDate,
            ),
        )
    }

    @Transactional
    fun patch(userId: Long, command: HomePropertyPatchCommand): HomeProperty {
        val property = homePropertyRepository.findByUserId(userId) ?: throw HomePropertyNotFoundException()

        property.address = command.address.requiredValueOr(property.address)
        property.depositAmount = command.depositAmount.requiredValueOr(property.depositAmount)
        property.buildingType = command.buildingType.requiredValueOr(property.buildingType)
        property.landlordName = command.landlordName.optionalValueOr(property.landlordName)
        property.plannedContractDate = command.plannedContractDate.optionalValueOr(property.plannedContractDate)

        return property
    }

    private fun String?.normalizeOptionalText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun <T> FieldPatch<T>.requiredValueOr(current: T): T =
        when (this) {
            FieldPatch.Unchanged -> current
            is FieldPatch.Set -> value
            FieldPatch.Clear -> throw InvalidHomePropertyRequestException()
        }

    private fun <T> FieldPatch<T>.optionalValueOr(current: T?): T? =
        when (this) {
            FieldPatch.Unchanged -> current
            is FieldPatch.Set -> value
            FieldPatch.Clear -> null
        }
}
