-- 분석 케이스와 선택 입력 서류·체크리스트를 저장하는 테이블
CREATE TABLE analysis_cases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    stage VARCHAR(32) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_analysis_cases_user_id (user_id),
    CONSTRAINT fk_analysis_cases_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_analysis_cases_property_id FOREIGN KEY (property_id) REFERENCES home_properties(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analysis_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_analysis_documents_case_type UNIQUE (case_id, document_type),
    CONSTRAINT fk_analysis_documents_case_id FOREIGN KEY (case_id) REFERENCES analysis_cases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analysis_checklist_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    item_key VARCHAR(100) NOT NULL,
    checked BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_analysis_answers_case_item UNIQUE (case_id, item_key),
    CONSTRAINT fk_analysis_answers_case_id FOREIGN KEY (case_id) REFERENCES analysis_cases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
