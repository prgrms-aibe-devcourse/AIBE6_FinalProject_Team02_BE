-- 챌린지 하드 삭제 시 리뷰/좋아요도 자동 정리되도록 FK(ON DELETE CASCADE) 추가.
-- review / review_like 는 지금까지 FK가 없어 챌린지를 지워도 고아로 남았다.

-- 1) FK 제약 위반 방지를 위해 기존 고아 데이터 먼저 정리
DELETE FROM review_like rl
 WHERE NOT EXISTS (SELECT 1 FROM review r WHERE r.id = rl.review_id);

DELETE FROM review r
 WHERE NOT EXISTS (SELECT 1 FROM challenge_dex c WHERE c.id = r.challenge_dex_id);

DELETE FROM review r
 WHERE r.slot_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM challenge_dex_slot s WHERE s.id = r.slot_id);

-- 2) CASCADE 성능용 인덱스 (Postgres는 FK 컬럼 인덱스를 자동 생성하지 않음)
CREATE INDEX IF NOT EXISTS idx_review_challenge_dex ON review (challenge_dex_id);
CREATE INDEX IF NOT EXISTS idx_review_slot          ON review (slot_id);
CREATE INDEX IF NOT EXISTS idx_review_like_review   ON review_like (review_id);

-- 3) FK 추가 (모두 ON DELETE CASCADE)
ALTER TABLE review
    ADD CONSTRAINT fk_review_challenge_dex FOREIGN KEY (challenge_dex_id)
        REFERENCES challenge_dex(id) ON DELETE CASCADE;

ALTER TABLE review
    ADD CONSTRAINT fk_review_slot FOREIGN KEY (slot_id)
        REFERENCES challenge_dex_slot(id) ON DELETE CASCADE;

ALTER TABLE review_like
    ADD CONSTRAINT fk_review_like_review FOREIGN KEY (review_id)
        REFERENCES review(id) ON DELETE CASCADE;
