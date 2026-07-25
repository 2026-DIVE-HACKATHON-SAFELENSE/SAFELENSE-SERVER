// 저장된 주소를 공공데이터 조회 코드로 변환하는 경계
package com.safelense.analysis.collection

fun interface PropertyAddressResolver {
    fun resolve(address: String): ResolvedPropertyAddress?
}
