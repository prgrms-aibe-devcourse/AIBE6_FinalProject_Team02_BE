-- 챌린지 개설권을 유저에 카운트로 보관 (매월 3개 리필). challenge_ticket 테이블 대체.
ALTER TABLE users ADD COLUMN challenge_ticket_count INT NOT NULL DEFAULT 3;
ALTER TABLE users ADD COLUMN challenge_ticket_month INT NOT NULL DEFAULT 0;

-- 티켓 테이블 방식 폐기
DROP TABLE IF EXISTS challenge_ticket;