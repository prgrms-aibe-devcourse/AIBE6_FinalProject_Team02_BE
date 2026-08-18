-- V2608181700: 리뷰 인덱스 정리 + 챌린지 리뷰 1개 제한을 DB로 내림
--
-- V36에서 리뷰를 한 테이블(review)에 FOOD/CHALLENGE 두 종류로 넣으면서 생긴 빈 자리 셋을 메운다.

-- ---------------------------------------------------------------------------
-- 1) 챌린지 리뷰 "챌린지당 1개"를 DB가 막게 한다
--
-- uq_review_food (reviewer_id, slot_id)는 챌린지 리뷰를 못 잡는다 — Postgres는 NULL을
-- 서로 다른 값으로 보기 때문에 slot_id가 NULL인 행은 몇 개든 들어간다. 지금까지는
-- ReviewService의 existsBy... 체크뿐이어서 동시 요청 두 개가 같이 통과할 수 있었다.
--
-- 부분 유니크 인덱스라 음식 리뷰(slot_id 있음)에는 영향이 없다.
-- ---------------------------------------------------------------------------

-- 이미 중복이 있으면 인덱스를 만들 수 없다. 가장 먼저 쓴 것만 남긴다
-- (좋아요 행부터 지운다 — review를 먼저 지우면 가리킬 대상 없는 좋아요가 남는다)
DELETE FROM review_like
WHERE review_id IN (
    SELECT id
    FROM (SELECT id,
                 ROW_NUMBER() OVER (PARTITION BY reviewer_id, challenge_dex_id
                                    ORDER BY created_at, id) AS rn
          FROM review
          WHERE review_type = 'CHALLENGE') ranked
    WHERE rn > 1
);

DELETE FROM review
WHERE id IN (
    SELECT id
    FROM (SELECT id,
                 ROW_NUMBER() OVER (PARTITION BY reviewer_id, challenge_dex_id
                                    ORDER BY created_at, id) AS rn
          FROM review
          WHERE review_type = 'CHALLENGE') ranked
    WHERE rn > 1
);

CREATE UNIQUE INDEX uq_review_challenge
    ON review (reviewer_id, challenge_dex_id)
    WHERE review_type = 'CHALLENGE';

-- ---------------------------------------------------------------------------
-- 2) 음식 리뷰 목록 인덱스
--
-- findBySlotIdOrderByLikeCountDescCreatedAtDesc — slot_id 단독 조회인데 받쳐 줄 인덱스가
-- 없었다. uq_review_food는 reviewer_id가 선두라 쓸 수 없다.
--
-- 정렬 컬럼까지 넣어 ORDER BY도 인덱스로 끝낸다. slot_id가 있는 행만 담아 크기를 줄인다
-- (slot_id = ? 조건이면 NULL이 아님이 자명하므로 부분 인덱스도 그대로 쓰인다)
-- ---------------------------------------------------------------------------
CREATE INDEX idx_review_slot
    ON review (slot_id, like_count DESC, created_at DESC)
    WHERE slot_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3) "내가 좋아요한 리뷰" 인덱스
--
-- uq_review_like (review_id, user_id)는 review_id가 선두라 user_id 단독 조회를 못 받는다.
-- 마이 → 내 활동의 findByUserIdOrderByCreatedAtDesc가 좋아요 전체를 훑게 된다.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_review_like_user
    ON review_like (user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 일부러 넣지 않은 것
--
--   - review (reviewer_id, created_at DESC) — "내가 쓴 리뷰"용. reviewer_id 선두 인덱스가
--     이미 둘(uq_review_food, uq_review_challenge) 있어 행을 좁히는 데 충분하고, 한 사람의
--     리뷰 수는 "해금한 슬롯 + 완주한 챌린지"가 상한이라 남은 정렬이 몇 행짜리다
--   - idx_review_target에 정렬 컬럼 덧붙이기 — 챌린지 리뷰는 챌린지당 완주자 수만큼이라
--     역시 작다
--
-- 쓰기마다 갱신 비용을 내는 물건이라, 눈에 보이는 이득이 없으면 두지 않는다
-- ---------------------------------------------------------------------------
