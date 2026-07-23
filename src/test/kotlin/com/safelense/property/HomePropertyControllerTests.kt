// 내 집 조회·등록·JSON Merge Patch HTTP 계약을 검증하는 MVC 테스트
package com.safelense.property

import com.safelense.auth.presentation.ApiExceptionHandler
import java.time.LocalDate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class HomePropertyControllerTests {
    private val service = mock(HomePropertyService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(HomePropertyController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `returns null when the authenticated user has no property`() {
        `when`(service.get(7L)).thenReturn(null)

        mockMvc.perform(
            get("/api/v1/me/property")
                .principal(UsernamePasswordAuthenticationToken(7L, null)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.property").doesNotExist())

        verify(service).get(7L)
    }

    @Test
    fun `returns the authenticated users property`() {
        `when`(service.get(7L)).thenReturn(property())

        mockMvc.perform(
            get("/api/v1/me/property")
                .principal(UsernamePasswordAuthenticationToken(7L, null)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.property.id").value(1))
            .andExpect(jsonPath("$.property.address").value("기존 주소"))
            .andExpect(jsonPath("$.property.depositAmount").value(25000))
            .andExpect(jsonPath("$.property.buildingType").value("MULTI_FAMILY"))
            .andExpect(jsonPath("$.property.landlordName").value("기존 임대인"))
            .andExpect(jsonPath("$.property.plannedContractDate").value("2026-08-01"))
    }

    @Test
    fun `creates the first property`() {
        val command = HomePropertyCreateCommand(
            address = "서울시 마포구 합정동 123-45",
            depositAmount = 25000L,
            buildingType = BuildingType.MULTI_FAMILY,
            landlordName = "홍길동",
            plannedContractDate = LocalDate.parse("2026-08-01"),
        )
        `when`(service.create(7L, command))
            .thenReturn(property())

        mockMvc.perform(
            post("/api/v1/me/property")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "address": "서울시 마포구 합정동 123-45",
                      "depositAmount": 25000,
                      "buildingType": "MULTI_FAMILY",
                      "landlordName": "홍길동",
                      "plannedContractDate": "2026-08-01"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.property.id").value(1))

        verify(service).create(7L, command)
    }

    @Test
    fun `rejects invalid create requests`() {
        mockMvc.perform(
            post("/api/v1/me/property")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "address": " ",
                      "depositAmount": -1,
                      "buildingType": "MULTI_FAMILY"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test
    fun `returns conflict when property already exists`() {
        val command = HomePropertyCreateCommand(
            address = "서울시 마포구 합정동 123-45",
            depositAmount = 25000L,
            buildingType = BuildingType.MULTI_FAMILY,
            landlordName = null,
            plannedContractDate = null,
        )
        `when`(service.create(7L, command))
            .thenThrow(HomePropertyAlreadyExistsException())

        mockMvc.perform(
            post("/api/v1/me/property")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "address": "서울시 마포구 합정동 123-45",
                      "depositAmount": 25000,
                      "buildingType": "MULTI_FAMILY"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("PROPERTY_ALREADY_EXISTS"))
    }

    @Test
    fun `patches only supplied fields and clears explicit nulls`() {
        val command = HomePropertyPatchCommand(
            depositAmount = FieldPatch.Set(30000L),
            landlordName = FieldPatch.Clear,
        )
        `when`(service.patch(7L, command))
            .thenReturn(property(depositAmount = 30000L, landlordName = null))

        mockMvc.perform(
            patch("/api/v1/me/property")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.valueOf("application/merge-patch+json"))
                .content("""{"depositAmount":30000,"landlordName":null}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.property.depositAmount").value(30000))
            .andExpect(jsonPath("$.property.landlordName").doesNotExist())

        verify(service).patch(7L, command)
    }

    @Test
    fun `returns not found when patch target does not exist`() {
        val command = HomePropertyPatchCommand(address = FieldPatch.Set("새 주소"))
        `when`(service.patch(7L, command))
            .thenThrow(HomePropertyNotFoundException())

        mockMvc.perform(
            patch("/api/v1/me/property")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.valueOf("application/merge-patch+json"))
                .content("""{"address":"새 주소"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"))
    }

    @Test
    fun `rejects empty unknown and required null patches`() {
        val invalidBodies = listOf(
            "{}",
            """{"unknown":"value"}""",
            """{"address":null}""",
        )

        invalidBodies.forEach { body ->
            mockMvc.perform(
                patch("/api/v1/me/property")
                    .principal(UsernamePasswordAuthenticationToken(7L, null))
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(body),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        }
    }

    @Test
    fun `rejects invalid patch values`() {
        val invalidBodies = listOf(
            """{"depositAmount":-1}""",
            """{"buildingType":"UNKNOWN"}""",
            """{"plannedContractDate":"2026-99-99"}""",
        )

        invalidBodies.forEach { body ->
            mockMvc.perform(
                patch("/api/v1/me/property")
                    .principal(UsernamePasswordAuthenticationToken(7L, null))
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(body),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        }
    }

    private fun property(
        depositAmount: Long = 25000L,
        landlordName: String? = "기존 임대인",
    ): HomeProperty =
        HomeProperty(
            id = 1L,
            userId = 7L,
            address = "기존 주소",
            depositAmount = depositAmount,
            buildingType = BuildingType.MULTI_FAMILY,
            landlordName = landlordName,
            plannedContractDate = LocalDate.parse("2026-08-01"),
        )
}
