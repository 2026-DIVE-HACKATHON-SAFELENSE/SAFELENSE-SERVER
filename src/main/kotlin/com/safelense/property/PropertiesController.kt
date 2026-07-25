// 인증 사용자의 후보 매물 목록을 제공하는 컨트롤러
package com.safelense.property

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class PropertiesEnvelope(
    val properties: List<HomePropertyResponse>,
)

@RestController
@RequestMapping("/api/v1/properties")
class PropertiesController(
    private val service: HomePropertyService,
) {
    @GetMapping
    fun list(authentication: Authentication): PropertiesEnvelope =
        PropertiesEnvelope(service.list(authentication.principal as Long).map(::toResponse))

    @GetMapping("/{propertyId}")
    fun get(
        authentication: Authentication,
        @PathVariable propertyId: Long,
    ): HomePropertyEnvelope =
        HomePropertyEnvelope(toResponse(service.get(authentication.principal as Long, propertyId)))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: HomePropertyCreateRequest,
    ): HomePropertyEnvelope =
        HomePropertyEnvelope(
            toResponse(
                service.create(
                    authentication.principal as Long,
                    HomePropertyCreateCommand(
                        request.address,
                        request.depositAmount,
                        request.buildingType,
                        request.landlordName,
                        request.plannedContractDate,
                    ),
                ),
            ),
        )

    private fun toResponse(property: HomeProperty): HomePropertyResponse =
        HomePropertyResponse(
            id = requireNotNull(property.id),
            address = property.address,
            depositAmount = property.depositAmount,
            buildingType = property.buildingType,
            landlordName = property.landlordName,
            plannedContractDate = property.plannedContractDate,
        )
}
