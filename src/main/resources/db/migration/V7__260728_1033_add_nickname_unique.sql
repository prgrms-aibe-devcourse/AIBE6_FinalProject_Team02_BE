-- 닉네임 유일성 보장: 앱단 existsByNickname 검사만으론 동시성(TOCTOU) 구멍이 있어 DB 제약을 건다.
-- PostgreSQL UNIQUE는 NULL을 서로 다르게 취급 → 미설정(null) 유저 다수 허용, 설정된 닉네임끼리만 유일성 강제.
-- (V2에서 소셜 로그인 대응으로 NOT NULL/UNIQUE를 함께 풀었으나, UNIQUE는 nullable과 양립하므로 재도입)
ALTER TABLE users ADD CONSTRAINT uk_users_nickname UNIQUE (nickname);
