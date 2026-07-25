// 공공데이터 조회용으로 정규화된 주소 코드와 좌표를 표현하는 값
package com.safelense.analysis.collection

data class ResolvedPropertyAddress(
    val pnu: String,
    val sigunguCode: String,
    val bjdongCode: String,
    val platGbCode: String,
    val bun: String,
    val ji: String,
    val province: String,
    val district: String,
    val legalDong: String,
    val longitude: Double,
    val latitude: Double,
)
