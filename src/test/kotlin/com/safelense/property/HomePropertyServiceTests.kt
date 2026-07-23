// 사용자별 내 집 조회·생성·부분 수정 규칙을 검증하는 서비스 테스트
package com.safelense.property

import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException

class HomePropertyServiceTests {
    private val repository = mock(HomePropertyRepository::class.java)
    private val service = HomePropertyService(repository)

    @Test
    fun `gets the property belonging to the authenticated user`() {
        val property = property(userId = 7L)
        `when`(repository.findByUserId(7L)).thenReturn(property)

        val result = service.get(7L)

        assertThat(result).isSameAs(property)
        verify(repository).findByUserId(7L)
    }

    @Test
    fun `creates the first property for a user`() {
        `when`(repository.findByUserId(7L)).thenReturn(null)
        `when`(repository.saveAndFlush(any(HomeProperty::class.java))).thenAnswer { it.arguments[0] }

        val result = service.create(
            7L,
            HomePropertyCreateCommand(
                address = "서울시 마포구 합정동 123-45",
                depositAmount = 25000L,
                buildingType = BuildingType.MULTI_FAMILY,
                landlordName = "홍길동",
                plannedContractDate = LocalDate.parse("2026-08-01"),
            ),
        )

        val captor = ArgumentCaptor.forClass(HomeProperty::class.java)
        verify(repository).saveAndFlush(captor.capture())
        assertThat(captor.value.userId).isEqualTo(7L)
        assertThat(result.address).isEqualTo("서울시 마포구 합정동 123-45")
    }

    @Test
    fun `rejects a second property for the same user`() {
        `when`(repository.findByUserId(7L)).thenReturn(property(userId = 7L))

        assertThatThrownBy {
            service.create(
                7L,
                HomePropertyCreateCommand(
                    address = "새 주소",
                    depositAmount = 30000L,
                    buildingType = BuildingType.APARTMENT,
                    landlordName = null,
                    plannedContractDate = null,
                ),
            )
        }.isInstanceOf(HomePropertyAlreadyExistsException::class.java)

        verify(repository, never()).saveAndFlush(any(HomeProperty::class.java))
    }

    @Test
    fun `maps a concurrent duplicate insert to already exists`() {
        `when`(repository.findByUserId(7L)).thenReturn(null)
        `when`(repository.saveAndFlush(any(HomeProperty::class.java)))
            .thenThrow(DataIntegrityViolationException("duplicate user_id"))

        assertThatThrownBy {
            service.create(
                7L,
                HomePropertyCreateCommand(
                    address = "새 주소",
                    depositAmount = 30000L,
                    buildingType = BuildingType.APARTMENT,
                    landlordName = null,
                    plannedContractDate = null,
                ),
            )
        }.isInstanceOf(HomePropertyAlreadyExistsException::class.java)
    }

    @Test
    fun `patches supplied fields and preserves omitted fields`() {
        val property = property(userId = 7L)
        `when`(repository.findByUserId(7L)).thenReturn(property)

        val result = service.patch(
            7L,
            HomePropertyPatchCommand(
                depositAmount = FieldPatch.Set(30000L),
            ),
        )

        assertThat(result.address).isEqualTo("기존 주소")
        assertThat(result.depositAmount).isEqualTo(30000L)
        assertThat(result.buildingType).isEqualTo(BuildingType.MULTI_FAMILY)
        assertThat(result.landlordName).isEqualTo("기존 임대인")
        assertThat(result.plannedContractDate).isEqualTo(LocalDate.parse("2026-08-01"))
    }

    @Test
    fun `clears optional fields when patch requests deletion`() {
        val property = property(userId = 7L)
        `when`(repository.findByUserId(7L)).thenReturn(property)

        val result = service.patch(
            7L,
            HomePropertyPatchCommand(
                landlordName = FieldPatch.Clear,
                plannedContractDate = FieldPatch.Clear,
            ),
        )

        assertThat(result.landlordName).isNull()
        assertThat(result.plannedContractDate).isNull()
    }

    @Test
    fun `rejects patch when the user has no property`() {
        `when`(repository.findByUserId(7L)).thenReturn(null)

        assertThatThrownBy {
            service.patch(
                7L,
                HomePropertyPatchCommand(address = FieldPatch.Set("새 주소")),
            )
        }.isInstanceOf(HomePropertyNotFoundException::class.java)
    }

    private fun property(userId: Long): HomeProperty =
        HomeProperty(
            id = 1L,
            userId = userId,
            address = "기존 주소",
            depositAmount = 25000L,
            buildingType = BuildingType.MULTI_FAMILY,
            landlordName = "기존 임대인",
            plannedContractDate = LocalDate.parse("2026-08-01"),
        )
}
