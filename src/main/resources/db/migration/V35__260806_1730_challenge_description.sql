ALTER TABLE challenge_dex DROP COLUMN challenge_type;
ALTER TABLE challenge_dex DROP COLUMN verify_type;
-- 목표 음식에 가게명·설명(선택) 추가 (위치 인증 개설 위저드)
ALTER TABLE challenge_dex_slot ADD COLUMN store_name VARCHAR(255);
ALTER TABLE challenge_dex_slot ADD COLUMN description VARCHAR(500);