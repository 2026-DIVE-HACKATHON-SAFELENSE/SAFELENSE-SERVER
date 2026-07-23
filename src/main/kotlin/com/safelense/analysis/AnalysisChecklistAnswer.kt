// 분석 케이스의 체크리스트 문항별 불리언 답변을 저장하는 엔티티
package com.safelense.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "analysis_checklist_answers")
class AnalysisChecklistAnswer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "case_id", nullable = false)
    val caseId: Long,
    @Column(name = "item_key", nullable = false, length = 100)
    val itemKey: String,
    @Column(nullable = false)
    val checked: Boolean,
)
