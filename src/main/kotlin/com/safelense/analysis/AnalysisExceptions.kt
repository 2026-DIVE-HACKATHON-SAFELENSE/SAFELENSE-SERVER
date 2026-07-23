// 분석 케이스 입력 API의 도메인 오류를 구분하는 예외
package com.safelense.analysis

class AnalysisCaseNotFoundException : RuntimeException()

class InvalidAnalysisDocumentException : RuntimeException()

class AnalysisDocumentTooLargeException : RuntimeException()

class AnalysisDocumentNotFoundException : RuntimeException()

class InvalidAnalysisChecklistException : RuntimeException()

class InvalidAnalysisResultRequestException : RuntimeException()

class AnalysisResultNotFoundException : RuntimeException()
