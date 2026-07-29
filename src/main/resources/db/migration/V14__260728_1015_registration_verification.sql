-- 등록 플로우 AI 검증 (PLAN "📷 도감 등록 상세 스펙")
--
-- V1의 food_candidate는 "AI가 음식을 맞히고 유저가 후보를 고르던" 시절의 설계다.
-- 현재는 유저가 도감 칸을 지정하고 AI는 일치 여부만 판정하므로 고를 후보가 없다.
-- 대신 이름별 판정 이력을 남겨 불일치 사유 표시와 오판정 모니터링에 쓴다.

ALTER TABLE registration
    -- AI에 보낸 단 한 장 (§5.2 분석 사진). 카드 사진과는 다른 개념이다
    ADD COLUMN analysis_photo_key VARCHAR(500),
    -- 재분석 상한 2회는 서버가 센다. 클라이언트가 세면 새로고침으로 우회된다
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN updated_at TIMESTAMP;

CREATE TABLE verification_attempt (
    id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    -- 1회차(최초) / 2·3회차(재시도). registration.retry_count + 1
    attempt_no INT NOT NULL,
    slot_id BIGINT NOT NULL,
    matched BOOLEAN NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    -- 불일치 사유. 통과면 빈 문자열
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_verification_attempt_registration
        FOREIGN KEY (registration_id) REFERENCES registration (id),
    CONSTRAINT fk_verification_attempt_slot
        FOREIGN KEY (slot_id) REFERENCES basic_dex (id)
);

CREATE INDEX idx_verification_attempt_registration
    ON verification_attempt (registration_id);

-- 후보 선택 설계의 잔재. 쓰는 코드가 없다
DROP TABLE IF EXISTS food_candidate;
