// 실제 비식별 임차인 상담 사례와 검색용 임베딩을 저장하는 엔티티
package com.safelense.analysis.match

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "consultation_cases")
class ConsultationCase(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "external_case_id", nullable = false, length = 64)
    val externalCaseId: String,
    @Column(nullable = false, length = 64)
    val source: String,
    @Column(name = "dataset_version", nullable = false, length = 32)
    var datasetVersion: String,
    @Column(name = "source_group", nullable = false, length = 100)
    var sourceGroup: String,
    @Column(name = "consultation_month", nullable = false, length = 7)
    var consultationMonth: String,
    @Column(nullable = false, length = 50)
    var province: String,
    @Column(nullable = false, length = 50)
    var district: String,
    @Column(name = "deposit_band", nullable = false, length = 50)
    var depositBand: String,
    @Column(name = "contract_status", nullable = false, length = 50)
    var contractStatus: String,
    @Column(name = "housing_type", nullable = false, length = 50)
    var housingType: String,
    @Column(name = "senior_rights", nullable = false, length = 100)
    var seniorRights: String,
    @Column(name = "guarantee_status", nullable = false, length = 100)
    var guaranteeStatus: String,
    @Column(name = "dispute_type", nullable = false, length = 100)
    var disputeType: String,
    @Column(name = "progress_stage", nullable = false, length = 100)
    var progressStage: String,
    @Column(name = "situation_summary", columnDefinition = "TEXT")
    var situationSummary: String? = null,
    @Column(name = "counselor_opinion", columnDefinition = "TEXT")
    var counselorOpinion: String? = null,
    @Column(name = "special_notes", columnDefinition = "TEXT")
    var specialNotes: String? = null,
    @Column(name = "embedding_json", columnDefinition = "TEXT")
    var embeddingJson: String? = null,
)
