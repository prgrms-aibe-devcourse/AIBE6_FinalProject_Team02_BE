-- 대표 뱃지(장착) 저장: 유저당 1개. badge 삭제 시 NULL 처리
-- (뱃지 마스터/획득 데이터는 운영진 등록·각 도메인 지급으로 채워지므로 여기서 관리하지 않음)
ALTER TABLE users ADD COLUMN equipped_badge_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_equipped_badge
    FOREIGN KEY (equipped_badge_id) REFERENCES badge(id) ON DELETE SET NULL;
