-- V2608201000: 내 활동(로그잇) 조회용 인덱스
--
-- 로그잇 좋아요·댓글은 지금까지 "대상 기준" 조회만 있었다. 마이 → 내 활동은 반대
-- 방향(사용자 기준)으로 훑는데, 기존 제약은 대상 컬럼이 선두라 받쳐 주지 못한다.
--
--   uq_made_dex_record_like (record_id, user_id)      ← user_id 단독 조회 불가
--   idx_made_dex_comment_record (made_dex_record_id)  ← author_id 단독 조회 불가
--
-- 정렬 컬럼까지 넣어 ORDER BY created_at DESC도 인덱스로 끝낸다.
-- 리뷰 쪽 idx_review_like_user(V2608200925)와 같은 빈자리다.

CREATE INDEX idx_made_dex_record_like_user
    ON made_dex_record_like (user_id, created_at DESC);

CREATE INDEX idx_made_dex_comment_author
    ON made_dex_comment (author_id, created_at DESC);
