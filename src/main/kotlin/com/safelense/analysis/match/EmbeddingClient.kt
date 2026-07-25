// 상담 검색 문장을 의미 벡터로 변환하는 경계
package com.safelense.analysis.match

class EmbeddingUnavailableException : RuntimeException()

fun interface EmbeddingClient {
    fun embed(inputs: List<String>): List<List<Double>>
}
