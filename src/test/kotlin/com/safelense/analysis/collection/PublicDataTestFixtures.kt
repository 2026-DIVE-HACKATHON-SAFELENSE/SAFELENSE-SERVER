// 공공데이터 클라이언트 테스트에서 공통으로 사용하는 정규화 주소 fixture
package com.safelense.analysis.collection

fun address() = ResolvedPropertyAddress(
    pnu = "1114010300100310000",
    sigunguCode = "11140",
    bjdongCode = "10300",
    platGbCode = "0",
    bun = "0031",
    ji = "0000",
    province = "서울특별시",
    district = "중구",
    legalDong = "태평로1가",
    longitude = 126.977829,
    latitude = 37.566317,
)
