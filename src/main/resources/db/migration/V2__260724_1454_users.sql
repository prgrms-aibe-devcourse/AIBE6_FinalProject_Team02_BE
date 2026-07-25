-- 1) email 컬럼 추가 (엔티티에 있으나 V1에 누락됨)
ALTER TABLE users ADD COLUMN email VARCHAR(320);

-- 2) role 컬럼 추가 (엔티티에서 NOT NULL).
--    기존 행이 있을 수 있으므로 기본값 'USER'로 채운 뒤 추가한다.
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 3) nickname 정책 정합화 (A 방향)
--    소셜 로그인 시 닉네임을 못 받는 경우가 있으므로 NOT NULL / UNIQUE를 해제하고,
--    엔티티(length = 30)와 길이를 맞춘다.
ALTER TABLE users ALTER COLUMN nickname DROP NOT NULL;
ALTER TABLE users ALTER COLUMN nickname TYPE VARCHAR(30);
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_nickname_key;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP;