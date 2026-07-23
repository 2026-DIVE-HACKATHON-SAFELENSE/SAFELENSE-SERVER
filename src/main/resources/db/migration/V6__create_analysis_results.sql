-- 사용자별 위험 분석 결과와 리포트 원본을 저장하는 테이블
CREATE TABLE analysis_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    stage VARCHAR(32) NOT NULL,
    score INT NULL,
    grade VARCHAR(16) NOT NULL,
    confidence INT NOT NULL,
    summary VARCHAR(500) NOT NULL,
    findings TEXT NOT NULL,
    recommendations TEXT NOT NULL,
    rule_version VARCHAR(32) NOT NULL,
    analyzed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_analysis_results_case_id UNIQUE (case_id),
    INDEX idx_analysis_results_user_id_id (user_id, id),
    INDEX idx_analysis_results_user_stage_id (user_id, stage, id),
    CONSTRAINT fk_analysis_results_case_id FOREIGN KEY (case_id) REFERENCES analysis_cases(id) ON DELETE CASCADE,
    CONSTRAINT fk_analysis_results_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_analysis_results_property_id FOREIGN KEY (property_id) REFERENCES home_properties(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
