-- 로그잇: 사람 × 끼니 × 날짜마다 기록 한 건 (CATCHEAT-53)
-- 아침은 하루에 한 번 먹는다. 다시 남기려면 고치거나 지우고 새로 쓴다.

-- ── 기존 중복 정리 ──────────────────────────────────────
-- 제약을 걸기 전에 이미 쌓인 중복을 없앤다. 마지막에 등록한 것만 남긴다.
-- 행을 지우지 않고 deleted_at을 채우는 이유는 조회 경로가 전부 이 컬럼으로 걸러지고,
-- 사진 행을 살려 둬야 나중에 S3 객체를 되짚을 수 있기 때문이다.
UPDATE made_dex_record r
SET deleted_at = CURRENT_TIMESTAMP
WHERE r.deleted_at IS NULL
  AND EXISTS (
      SELECT 1
      FROM made_dex_record keep
      WHERE keep.deleted_at IS NULL
        AND keep.made_dex_id = r.made_dex_id
        AND keep.slot_id = r.slot_id
        AND keep.author_id = r.author_id
        AND keep.logged_on = r.logged_on
        -- created_at이 같은 초에 몰릴 수 있어 id로 순서를 확정한다
        AND (keep.created_at, keep.id) > (r.created_at, r.id)
  );

-- ── 유니크 제약 ────────────────────────────────────────
-- 서비스에서 선검사를 하지만 그것만으로는 동시 요청 둘이 함께 통과한다.
-- 부분 인덱스라 소프트 삭제된 기록은 자리를 차지하지 않는다 — 지우고 다시 쓸 수 있다.
CREATE UNIQUE INDEX uq_made_dex_record_slot_author_day
    ON made_dex_record (made_dex_id, slot_id, author_id, logged_on)
    WHERE deleted_at IS NULL;
