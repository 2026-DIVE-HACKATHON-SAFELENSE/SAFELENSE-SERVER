-- 사용자별 현재 내 집 정보를 한 건 저장하는 테이블
CREATE TABLE home_properties (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    address VARCHAR(500) NOT NULL,
    deposit_amount BIGINT NOT NULL,
    building_type VARCHAR(32) NOT NULL,
    landlord_name VARCHAR(100) NULL,
    planned_contract_date DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_home_properties_user_id UNIQUE (user_id),
    CONSTRAINT fk_home_properties_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
