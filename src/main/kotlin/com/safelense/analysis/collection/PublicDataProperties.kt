// 공공데이터포털 건축물대장과 전월세 API 설정을 바인딩하는 속성
package com.safelense.analysis.collection

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.public-data")
data class PublicDataProperties(
    val serviceKey: String,
    val buildingBaseUrl: String = "https://apis.data.go.kr/1613000/BldRgstHubService",
    val apartmentRentBaseUrl: String = "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent",
    val officetelRentBaseUrl: String = "https://apis.data.go.kr/1613000/RTMSDataSvcOffiRent",
    val detachedRentBaseUrl: String = "https://apis.data.go.kr/1613000/RTMSDataSvcSHRent",
)
