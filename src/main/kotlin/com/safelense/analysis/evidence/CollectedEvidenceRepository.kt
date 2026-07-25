// 분석 실행별 정규화 근거를 저장하는 JPA 저장소
package com.safelense.analysis.evidence

import org.springframework.data.jpa.repository.JpaRepository

interface CollectedEvidenceRepository : JpaRepository<CollectedEvidence, Long>
