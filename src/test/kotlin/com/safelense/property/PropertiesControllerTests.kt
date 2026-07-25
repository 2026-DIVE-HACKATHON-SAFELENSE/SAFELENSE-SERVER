// 여러 후보 매물의 생성과 목록 조회 HTTP 계약을 검증하는 테스트
package com.safelense.property

import com.safelense.auth.presentation.ApiExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PropertiesControllerTests {
    @Test
    fun `creates another candidate property`() {
        val service = mock(HomePropertyService::class.java)
        val command = HomePropertyCreateCommand("서울시 중구 1", 20000, BuildingType.APARTMENT, null, null)
        `when`(service.create(7L, command)).thenReturn(property())
        val mockMvc = MockMvcBuilders.standaloneSetup(PropertiesController(service)).setControllerAdvice(ApiExceptionHandler()).build()

        mockMvc.perform(
            post("/api/v1/properties")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"address":"서울시 중구 1","depositAmount":20000,"buildingType":"APARTMENT"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.property.id").value(1))
    }

    @Test
    fun `rejects a zero deposit`() {
        val mockMvc = MockMvcBuilders.standaloneSetup(PropertiesController(mock(HomePropertyService::class.java))).setControllerAdvice(ApiExceptionHandler()).build()

        mockMvc.perform(
            post("/api/v1/properties")
                .principal(UsernamePasswordAuthenticationToken(7L, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"address":"서울시 중구 1","depositAmount":0,"buildingType":"APARTMENT"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `lists the authenticated users candidate properties`() {
        val service = mock(HomePropertyService::class.java)
        `when`(service.list(7L)).thenReturn(emptyList())
        val mockMvc = MockMvcBuilders.standaloneSetup(PropertiesController(service)).setControllerAdvice(ApiExceptionHandler()).build()

        mockMvc.perform(
            get("/api/v1/properties")
                .principal(UsernamePasswordAuthenticationToken(7L, null)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.properties").isArray)

        verify(service).list(7L)
    }

    @Test
    fun `gets a candidate property by its id`() {
        val service = mock(HomePropertyService::class.java)
        `when`(service.get(7L, 1L)).thenReturn(property())
        val mockMvc = MockMvcBuilders.standaloneSetup(PropertiesController(service)).setControllerAdvice(ApiExceptionHandler()).build()

        mockMvc.perform(
            get("/api/v1/properties/1")
                .principal(UsernamePasswordAuthenticationToken(7L, null)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.property.id").value(1))

        verify(service).get(7L, 1L)
    }

    private fun property() = HomeProperty(1L, 7L, "서울시 중구 1", 20000, BuildingType.APARTMENT)
}
