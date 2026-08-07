-- V36: 리뷰 분리 — 음식 리뷰(해금 후)·챌린지 리뷰(완료 후) 통합 + 좋아요
CREATE TABLE review (
                        id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        reviewer_id       BIGINT       NOT NULL,              -- 작성자(user id)
                        review_type       VARCHAR(20)  NOT NULL,              -- FOOD | CHALLENGE
                        challenge_dex_id  BIGINT       NOT NULL,              -- 어느 챌린지
                        slot_id           BIGINT       NULL,                  -- FOOD면 대상 슬롯, CHALLENGE면 NULL
                        content           VARCHAR(500),
                        rating            INT,                                -- 1~5 (선택)
                        like_count        INT          NOT NULL DEFAULT 0,    -- 목록 성능용 비정규화 카운터
                        created_at        DATETIME(6)  NOT NULL,
                        updated_at        DATETIME(6)
);

-- 음식 리뷰: 한 사람이 한 슬롯에 1개
CREATE UNIQUE INDEX uq_review_food ON review (reviewer_id, slot_id);
-- 조회용
CREATE INDEX idx_review_target ON review (review_type, challenge_dex_id);

CREATE TABLE review_like (
                             id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
                             review_id   BIGINT      NOT NULL,
                             user_id     BIGINT      NOT NULL,
                             created_at  DATETIME(6) NOT NULL,
                             CONSTRAINT uq_review_like UNIQUE (review_id, user_id)  -- 중복 좋아요 방지
);