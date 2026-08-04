-- ============================================================
-- 제작 도감 초대 코드 무효화 컬럼
-- 그룹당 유효 코드는 1개. 재발급하면 이전 코드에 revoked_at을 찍어 즉시 죽인다.
-- expires_at을 과거로 당기는 방식은 "언제 왜 죽었는지"를 잃어버려 쓰지 않는다.
-- ============================================================

ALTER TABLE made_dex_invite ADD COLUMN revoked_at TIMESTAMP;

-- 유효 코드 조회(참여자 관리 화면 진입 시)는 항상 revoked_at IS NULL로 걸린다
CREATE INDEX idx_made_dex_invite_active ON made_dex_invite (made_dex_id)
    WHERE revoked_at IS NULL;
