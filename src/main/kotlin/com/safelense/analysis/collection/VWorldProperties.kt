// VWorld 주소와 공동주택가격 API 설정을 바인딩하는 속성
package com.safelense.analysis.collection

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.vworld")
data class VWorldProperties(
    val apiKey: String,
    val searchBaseUrl: String = "https://api.vworld.kr/req/search",
    val officialPriceBaseUrl: String = "https://api.vworld.kr/ned/data/getApartHousingPriceAttr",
)
