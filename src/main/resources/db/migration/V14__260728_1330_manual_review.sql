-- 수동 폴백 — 검토 통과 후 해금
--
-- 재분석 상한을 넘긴 등록은 카드를 만들되 도감 칸은 열지 않는다.
-- 관리자가 검토를 수락하는 순간 칸이 열리고 수집률에 반영된다.
--
-- 해금 여부를 별도 플래그로 두지 않고 user_collection_id의 NULL 여부로 나타낸다:
--   user_collection_id IS NULL  → 검토 대기 (칸이 안 열림, 수집률 미반영, 배지 표시)
--   user_collection_id IS NOT NULL → 해금됨
-- 이렇게 하면 §5.2가 못박은 "검증 상태 값 2개"(PHOTO_VERIFIED / MANUAL_PENDING)를 늘리지 않아도 된다.
-- verification_status는 "어떻게 인증했는가", user_collection_id는 "열렸는가"로 축이 다르다.

ALTER TABLE collection_card ALTER COLUMN user_collection_id DROP NOT NULL;

-- 해금 전에는 user_collection이 없어 어느 칸인지 알 수 없다. 카드가 직접 들고 있어야 한다
ALTER TABLE collection_card ADD COLUMN slot_id BIGINT;

UPDATE collection_card c
   SET slot_id = uc.slot_id
  FROM user_collection uc
 WHERE uc.id = c.user_collection_id;

ALTER TABLE collection_card ALTER COLUMN slot_id SET NOT NULL;
ALTER TABLE collection_card
    ADD CONSTRAINT fk_collection_card_slot FOREIGN KEY (slot_id) REFERENCES basic_dex (id);

CREATE INDEX idx_collection_card_pending
    ON collection_card (user_collection_id) WHERE user_collection_id IS NULL;

-- 검토는 등록 건이 아니라 카드 단위다. 한 등록 건에서 일부는 통과하고 일부는 대기할 수 있다
ALTER TABLE review_queue_item ADD COLUMN collection_card_id BIGINT;
ALTER TABLE review_queue_item
    ADD CONSTRAINT fk_review_queue_item_card FOREIGN KEY (collection_card_id) REFERENCES collection_card (id);
ALTER TABLE review_queue_item ADD COLUMN reviewed_at TIMESTAMP;
ALTER TABLE review_queue_item ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE review_queue_item ADD COLUMN updated_at TIMESTAMP;

-- 기존 행이 없으므로 바로 NOT NULL로 굳힌다
ALTER TABLE review_queue_item ALTER COLUMN collection_card_id SET NOT NULL;

-- 같은 카드가 큐에 두 번 들어가지 않는다
ALTER TABLE review_queue_item ADD CONSTRAINT uk_review_queue_card UNIQUE (collection_card_id);

CREATE INDEX idx_review_queue_status ON review_queue_item (status);
