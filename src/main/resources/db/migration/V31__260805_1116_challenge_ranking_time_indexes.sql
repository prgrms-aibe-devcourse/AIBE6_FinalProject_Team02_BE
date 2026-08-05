-- 랭킹의 "최근 7일" 필터 대비 인덱스.
-- 현재 쿼리는 dex 범위로 먼저 좁혀 기존 인덱스로 커버되나,
-- 전체 통틀어 최근 N일 스캔류 미래 쿼리를 대비해 시간 컬럼 단독 인덱스를 선반영한다.
-- (V30은 타 작업자 예약 → V31 사용)
CREATE INDEX idx_challenge_participant_joined_at ON challenge_participant (joined_at);
CREATE INDEX idx_challenge_unlock_unlocked_at   ON challenge_unlock (unlocked_at);
