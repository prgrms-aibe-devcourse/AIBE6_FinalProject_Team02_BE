-- 베이짓 New 스티커를 이미 확인한 칸을 기록한다.
-- NULL이면 아직 안 봤다는 뜻이고, 24시간 창과 AND로 결합된다:
--   New = (칸이 열린 지 24시간 내) AND (아직 안 봄)
-- 24시간은 탭하지 않은 칸의 backstop으로 남는다 — 없으면 안 본 New가 영구히 남는다.
--
-- 기존 행은 NULL(안 봄)로 시작하지만 created_at이 이미 24시간을 지났으므로
-- 잘못된 New가 뜨지 않는다. 그래서 백필이 필요 없다.
ALTER TABLE user_collection ADD COLUMN new_badge_seen_at TIMESTAMP;
