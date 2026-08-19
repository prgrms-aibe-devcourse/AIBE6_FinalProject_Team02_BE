-- 소프트 삭제 폐기(하드 삭제로 전환).
-- 남아있던 소프트 삭제분은 물리 삭제(FK CASCADE로 자식 정리)한 뒤 컬럼 제거.
DELETE FROM challenge_dex WHERE deleted_at IS NOT NULL;
ALTER TABLE challenge_dex DROP COLUMN deleted_at;
