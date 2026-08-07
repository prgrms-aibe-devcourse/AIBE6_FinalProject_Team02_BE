-- 로그잇 끼니 슬롯과 식사 기록 스키마

-- 그룹마다 이름과 개수가 달라 전역 코드값으로 두지 않는다
CREATE TABLE made_dex_slot (
    id BIGSERIAL PRIMARY KEY,
    made_dex_id BIGINT NOT NULL,
    name VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL,
    hidden_at TIMESTAMP,                              -- 기록이 붙은 슬롯은 삭제 대신 이 값을 채운다
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_made_dex_slot_dex FOREIGN KEY (made_dex_id) REFERENCES made_dex(id) ON DELETE CASCADE,
    -- PK와 겹치지만, 기록의 복합 FK가 참조하려면 이 조합에 유니크가 있어야 한다
    CONSTRAINT uk_made_dex_slot_id_dex UNIQUE (id, made_dex_id)
);
CREATE INDEX idx_made_dex_slot_dex ON made_dex_slot (made_dex_id, sort_order);

-- 숨긴 슬롯은 화면에서 빠지므로 같은 이름을 다시 만들 수 있어야 한다
CREATE UNIQUE INDEX uk_made_dex_slot_name
    ON made_dex_slot (made_dex_id, name)
    WHERE hidden_at IS NULL;


-- made_dex_id는 slot을 타면 알 수 있지만, 피드 조회가 (그룹, 날짜)로 들어와 조인을 한 단계 줄인다.
-- 비정규화한 값이 슬롯의 도감과 어긋나지 않도록 아래 복합 FK로 묶는다
CREATE TABLE made_dex_record (
    id BIGSERIAL PRIMARY KEY,
    made_dex_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    logged_on DATE NOT NULL,
    memo VARCHAR(100),
    location_name VARCHAR(255),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_made_dex_record_dex FOREIGN KEY (made_dex_id) REFERENCES made_dex(id) ON DELETE CASCADE,
    -- 다른 도감의 슬롯 id를 넣으면 저장 자체가 막힌다
    CONSTRAINT fk_made_dex_record_slot FOREIGN KEY (slot_id, made_dex_id)
        REFERENCES made_dex_slot(id, made_dex_id),
    CONSTRAINT fk_made_dex_record_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_made_dex_record_feed ON made_dex_record (made_dex_id, logged_on);
CREATE INDEX idx_made_dex_record_slot ON made_dex_record (slot_id);
CREATE INDEX idx_made_dex_record_author ON made_dex_record (author_id);


-- 장수 상한(8장)은 AI 비용이 아니라 화면·용량 기준이라 애플리케이션에서 강제한다
CREATE TABLE made_dex_record_photo (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL,
    image_key VARCHAR(512) NOT NULL,
    sort_order INT NOT NULL,                          -- 0번이 카드 썸네일
    CONSTRAINT fk_made_dex_record_photo_record FOREIGN KEY (record_id) REFERENCES made_dex_record(id) ON DELETE CASCADE
);
CREATE INDEX idx_made_dex_record_photo_record ON made_dex_record_photo (record_id, sort_order);


-- 냉장고 화면이 이름으로 묶어 세므로 컬럼이 아니라 별도 테이블로 둔다
CREATE TABLE made_dex_record_food (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL,
    food_name VARCHAR(100) NOT NULL,                  -- basic_dex를 참조하지 않는 자유 입력
    sort_order INT NOT NULL,
    CONSTRAINT fk_made_dex_record_food_record FOREIGN KEY (record_id) REFERENCES made_dex_record(id) ON DELETE CASCADE
);
CREATE INDEX idx_made_dex_record_food_record ON made_dex_record_food (record_id, sort_order);
CREATE INDEX idx_made_dex_record_food_name ON made_dex_record_food (food_name);


-- V23의 자유 기록 테이블. 엔티티가 붙은 적이 없어 데이터도 참조 코드도 없다
DROP TABLE IF EXISTS made_dex_entry;
