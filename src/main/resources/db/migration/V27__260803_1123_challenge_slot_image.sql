-- 챌린지 슬롯에 개설자가 등록한 "목표 음식 사진"(S3 object key) 추가
-- 상세 도감에서 미해금 슬롯은 이 사진을 흑백으로, 인증(해금) 시 컬러로 표시
ALTER TABLE challenge_dex_slot
    ADD COLUMN image_key VARCHAR(512);
