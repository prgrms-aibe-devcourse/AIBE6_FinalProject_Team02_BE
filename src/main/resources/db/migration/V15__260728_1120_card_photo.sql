-- 카드 사진 N장 지원 (AGENTS.md §5.2 "스키마 미반영" 항목)
--
-- V1은 collection_card.representative_photo_id 하나로 "카드당 사진 1장"을 전제했다.
-- 실제 기획은 카드당 1~5장이고, 같은 사진을 여러 카드에 붙이는 것도 정상 동작이다
-- (한 상 사진 1장 → 여러 칸 해금). 그래서 연결 테이블이 필요하다.
--
-- representative_photo_id는 그대로 두되 의미를 "카드 썸네일"로 확정한다 —
-- 카드 상세의 첫 장이며, 도감 그리드 셀(운영진 일러스트)과는 무관하다.

CREATE TABLE card_photo (
    id BIGSERIAL PRIMARY KEY,
    collection_card_id BIGINT NOT NULL,
    photo_id BIGINT NOT NULL,
    -- 카드 상세 캐러셀에서 보여줄 순서
    sort_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_card_photo_card FOREIGN KEY (collection_card_id) REFERENCES collection_card (id),
    CONSTRAINT fk_card_photo_photo FOREIGN KEY (photo_id) REFERENCES photo (id),
    -- 한 카드에 같은 사진이 두 번 붙지는 않는다
    CONSTRAINT uk_card_photo UNIQUE (collection_card_id, photo_id)
);

CREATE INDEX idx_card_photo_card ON card_photo (collection_card_id);

-- 엔티티가 BaseEntity를 상속하므로 감사 컬럼을 맞춘다
ALTER TABLE photo ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE photo ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE collection_card ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE user_collection ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE user_collection ADD COLUMN updated_at TIMESTAMP;

-- collection_card.created_at은 V1에 없다. collected_at과 별개로 감사용으로 둔다
ALTER TABLE collection_card ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 별 랭크는 최대 3 (§5.1). DB에서도 넘지 못하게 막는다
ALTER TABLE user_collection
    ADD CONSTRAINT ck_user_collection_rank CHECK (rank BETWEEN 1 AND 3);
