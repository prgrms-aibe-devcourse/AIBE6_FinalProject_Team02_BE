-- =============================================================================
-- 개발용 더미 데이터: "다른 사람이 만든" 챌린지 3개 + 참여/완주/리뷰
-- =============================================================================
-- 조건
--   - 챌린지 3개 (개설자 = 더미 유저 '김맛집', 현재 로그인 유저 아님)
--   - 챌린지당 음식(슬롯) 10개, 장소는 실존 가게/주소 (좌표는 근사값)
--   - 챌린지당 참여자 10명 (챌린지마다 다른 10명)
--   - 완주자 8명 (10개 전부 해금 + completed_at), 미완주 2명(6개/3개 해금)
--   - 음식 리뷰: 해금한 모든 슬롯에 작성 완료 / 챌린지 리뷰: 완주자 8명 작성
--   - 리뷰 좋아요: 같은 챌린지 다른 참여자 0~3명 (중복 방지 반영)
--
-- 특징: PL/pgSQL(DO 블록)·TEMP 테이블 없이 순수 SQL 문장으로만 구성
--       → psql / DataGrip / IntelliJ 어디서 실행하든 동일하게 동작.
-- 실행: 이 파일 전체를 대상 DB(catcheat_dev)에서 "위에서 아래로" 실행.
-- 재실행 가능: 앞에서 기존 seed 데이터를 지우고 다시 넣는다.
-- ⚠ 운영 DB에서는 절대 실행하지 말 것. (Flyway 대상 폴더 db/migration 밖에 둠)
-- =============================================================================

-- 0) 기존 seed 정리 (provider_id 'seedcc_%' 식별) -----------------------------
DELETE FROM review_like
 WHERE review_id IN (SELECT id FROM review WHERE reviewer_id IN
     (SELECT id FROM users WHERE provider = 'KAKAO' AND provider_id LIKE 'seedcc_%'));

DELETE FROM review
 WHERE reviewer_id IN (SELECT id FROM users WHERE provider = 'KAKAO' AND provider_id LIKE 'seedcc_%');

DELETE FROM challenge_dex
 WHERE owner_id IN (SELECT id FROM users WHERE provider = 'KAKAO' AND provider_id = 'seedcc_owner');

DELETE FROM users WHERE provider = 'KAKAO' AND provider_id LIKE 'seedcc_%';

-- 1) seed 유저: 개설자 1 + 참여자 30 ----------------------------------------
INSERT INTO users (provider, provider_id, nickname, email, role, onboarding_completed)
VALUES ('KAKAO', 'seedcc_owner', '김맛집', 'seed_owner@catcheat.dev', 'USER', true);

INSERT INTO users (provider, provider_id, nickname, email, role, onboarding_completed)
SELECT 'KAKAO', 'seedcc_p' || lpad(g::text, 2, '0'), nk, 'seed_p' || g || '@catcheat.dev', 'USER', true
FROM (VALUES
    (1, '푸드러버'), (2, '미식가공주'), (3, '먹깨비'), (4, '배부른하마'), (5, '든든한끼'),
    (6, '야식요정'), (7, '맛동산러'), (8, '서울촌놈'), (9, '냉면광'), (10, '국밥형'),
    (11, '빵순이'), (12, '카페인중독'), (13, '디저트헌터'), (14, '골목대장'), (15, '혼밥러'),
    (16, '대식가'), (17, '소식좌'), (18, '맵찔이'), (19, '단짠단짠'), (20, '라면킬러'),
    (21, '치맥러버'), (22, '국수당기는날'), (23, '분식조아'), (24, '오늘도폭식'), (25, '다이어트내일'),
    (26, '위대한위장'), (27, '맛집탐험대'), (28, '존맛탱'), (29, '겉바속촉'), (30, '입짧은햇님')
) AS t(g, nk);

-- 2) 챌린지 3개 생성 (상시형, 30일 전 개설) ---------------------------------
INSERT INTO challenge_dex (owner_id, name, description, period_type, starts_at, ends_at, is_event, created_at)
SELECT (SELECT id FROM users WHERE provider = 'KAKAO' AND provider_id = 'seedcc_owner'),
       v.name, v.description, 'PERMANENT', now() - interval '30 days', NULL, false, now() - interval '30 days'
FROM (VALUES
    ('서울 노포 도장깨기', '수십 년 내공의 서울 노포를 도장깨기! 진짜 맛을 아는 사람들의 리스트.'),
    ('서울 카페·베이커리 성지순례', '성수부터 연남까지, 줄 서서 먹는 인기 카페·베이커리 10곳.'),
    ('부산 먹킷리스트', '바다 건너 부산까지! 부산 사람도 인정하는 진짜 먹거리 10선.')
) AS v(name, description);

-- 3) 슬롯 10개씩 (챌린지 이름으로 매핑) -------------------------------------
INSERT INTO challenge_dex_slot (challenge_dex_id, food_name, place_name, lat, lng, slot_order, store_name, description, image_key)
SELECT c.id, v.food_name, v.place_name, v.lat, v.lng, v.slot_order, v.store_name, v.description, NULL
FROM (VALUES
    -- 챌린지 1: 서울 노포 도장깨기
    ('서울 노포 도장깨기', 0, '평양냉면', '우래옥', '서울 중구 창경궁로 62-29', 37.5686, 126.9967, '슴슴한 육수의 정석'),
    ('서울 노포 도장깨기', 1, '곰탕', '하동관', '서울 중구 명동9길 12', 37.5647, 126.9838, '점심에만 열어요, 서두르세요'),
    ('서울 노포 도장깨기', 2, '설렁탕', '이문설농탕', '서울 종로구 우정국로 38-13', 37.5713, 126.9840, '100년 넘은 노포'),
    ('서울 노포 도장깨기', 3, '육개장', '부민옥', '서울 중구 다동길 24-12', 37.5679, 126.9800, '얼큰한 국물이 일품'),
    ('서울 노포 도장깨기', 4, '김치찌개', '은주정', '서울 중구 마른내로4길 19', 37.5648, 126.9950, '점심 김치찌개 인기'),
    ('서울 노포 도장깨기', 5, '짬뽕', '안동장', '서울 중구 을지로3길 24', 37.5660, 126.9910, '옛날식 하얀짬뽕'),
    ('서울 노포 도장깨기', 6, '평양냉면', '필동면옥', '서울 중구 서애로 26', 37.5606, 126.9942, '깔끔한 면발'),
    ('서울 노포 도장깨기', 7, '손만두', '자하손만두', '서울 종로구 백석동길 12', 37.5980, 126.9660, '부암동 손만두 명가'),
    ('서울 노포 도장깨기', 8, '즉석떡볶이', '애플하우스', '서울 용산구 신흥로 30', 37.5410, 126.9870, '해방촌 노포 떡볶이'),
    ('서울 노포 도장깨기', 9, '노가리', '을지OB베어', '서울 중구 을지로13길 19', 37.5665, 126.9915, '노가리 골목 원조'),
    -- 챌린지 2: 서울 카페·베이커리 성지순례
    ('서울 카페·베이커리 성지순례', 0, '드립커피', '블루보틀 성수', '서울 성동구 아차산로 7', 37.5432, 127.0560, '성수 랜드마크 카페'),
    ('서울 카페·베이커리 성지순례', 1, '크림라떼', '어니언 성수', '서울 성동구 아차산로9길 8', 37.5443, 127.0578, '팡도르 꼭 드세요'),
    ('서울 카페·베이커리 성지순례', 2, '베이글', '런던베이글뮤지엄 안국', '서울 종로구 북촌로4길 20', 37.5787, 126.9853, '오픈런 필수'),
    ('서울 카페·베이커리 성지순례', 3, '도넛', '노티드 청담', '서울 강남구 도산대로53길 15', 37.5240, 127.0430, '우유생크림 도넛'),
    ('서울 카페·베이커리 성지순례', 4, '에스프레소', '앤트러사이트 합정', '서울 마포구 토정로5길 10', 37.5480, 126.9110, '옛 신발공장 개조'),
    ('서울 카페·베이커리 성지순례', 5, '핸드드립', '프릳츠 도화점', '서울 마포구 새창로2길 17', 37.5390, 126.9490, '물개 로고 그 집'),
    ('서울 카페·베이커리 성지순례', 6, '아메리카노', '테라로사 광화문', '서울 종로구 종로1길 50', 37.5720, 126.9780, '넓고 쾌적'),
    ('서울 카페·베이커리 성지순례', 7, '소금빵', '카페 레이어드 연남', '서울 마포구 성미산로161-4', 37.5620, 126.9250, '스콘도 맛집'),
    ('서울 카페·베이커리 성지순례', 8, '티라미수', '성수연방', '서울 성동구 성수이로14길 14', 37.5417, 127.0575, '복합문화공간'),
    ('서울 카페·베이커리 성지순례', 9, '휘낭시에', '밀도 서울숲', '서울 성동구 왕십리로 96', 37.5470, 127.0440, '식빵이 유명해요'),
    -- 챌린지 3: 부산 먹킷리스트
    ('부산 먹킷리스트', 0, '돼지국밥', '초량돼지국밥', '부산 동구 중앙대로 228', 35.1150, 129.0400, '부산 국밥의 정석'),
    ('부산 먹킷리스트', 1, '밀면', '가야밀면', '부산 부산진구 가야대로482번길 9', 35.1560, 129.0450, '여름엔 역시 밀면'),
    ('부산 먹킷리스트', 2, '어묵', '삼진어묵 영도본점', '부산 영도구 태종로99번길 36', 35.0910, 129.0430, '어묵 크로켓 강추'),
    ('부산 먹킷리스트', 3, '씨앗호떡', 'BIFF광장 씨앗호떡', '부산 중구 비프광장로 36', 35.0980, 129.0270, '겨울 별미'),
    ('부산 먹킷리스트', 4, '회', '자갈치시장', '부산 중구 자갈치해안로 52', 35.0966, 129.0304, '오이소 보이소'),
    ('부산 먹킷리스트', 5, '단팥빵', '백구당', '부산 중구 대청로129번길 12', 35.1010, 129.0335, '80년 노포 빵집'),
    ('부산 먹킷리스트', 6, '완당', '18번완당집', '부산 중구 광복로56번길 12-1', 35.0990, 129.0320, '부산식 완탕'),
    ('부산 먹킷리스트', 7, '한우갈비', '소문난암소갈비집', '부산 해운대구 중동2로10번길 32-10', 35.1630, 129.1640, '해운대 갈비 명가'),
    ('부산 먹킷리스트', 8, '팥빙수', '옵스 남천점', '부산 수영구 광남로 91', 35.1460, 129.1120, '부산 인기 빵집'),
    ('부산 먹킷리스트', 9, '물냉면', '내호냉면', '부산 남구 우암번영로 26-1', 35.1310, 129.0620, '부산 밀면·냉면 원조')
) AS v(cname, slot_order, food_name, store_name, place_name, lat, lng, description)
JOIN challenge_dex c
  ON c.name = v.cname
 AND c.owner_id = (SELECT id FROM users WHERE provider = 'KAKAO' AND provider_id = 'seedcc_owner');

-- 4) 참여자 10명 (챌린지마다 다른 유저, 완주자 8: g<=8) -----------------------
-- 참여자 유저 provider_id 'seedcc_pNN' 에서 NN → (cno, g[1..10]) 를 유도
INSERT INTO challenge_participant (challenge_dex_id, user_id, joined_at, completed_at)
SELECT c.id, su.user_id,
       now() - interval '30 days' + make_interval(hours => su.g),
       CASE WHEN su.g <= 8
            THEN now() - interval '30 days' + interval '10 days' + make_interval(hours => su.g)
            ELSE NULL END
FROM (
    SELECT u.id AS user_id,
           ((cast(substring(u.provider_id from 'seedcc_p([0-9]+)') AS int) - 1) / 10) + 1 AS cno,
           ((cast(substring(u.provider_id from 'seedcc_p([0-9]+)') AS int) - 1) % 10) + 1 AS g
    FROM users u
    WHERE u.provider = 'KAKAO' AND u.provider_id LIKE 'seedcc_p%'
) su
JOIN (VALUES
    (1, '서울 노포 도장깨기'), (2, '서울 카페·베이커리 성지순례'), (3, '부산 먹킷리스트')
) m(cno, cname) ON m.cno = su.cno
JOIN challenge_dex c
  ON c.name = m.cname
 AND c.owner_id = (SELECT id FROM users WHERE provider = 'KAKAO' AND provider_id = 'seedcc_owner');

-- 5) 해금 (완주=10, g=9 → 6개, g=10 → 3개) -----------------------------------
INSERT INTO challenge_unlock (challenge_participant_id, slot_id, image_key, unlocked_at)
SELECT p.id, s.id, NULL,
       now() - interval '30 days' + make_interval(days => su.g, hours => s.slot_order)
FROM challenge_participant p
JOIN (
    SELECT u.id AS user_id,
           ((cast(substring(u.provider_id from 'seedcc_p([0-9]+)') AS int) - 1) % 10) + 1 AS g
    FROM users u
    WHERE u.provider = 'KAKAO' AND u.provider_id LIKE 'seedcc_p%'
) su ON su.user_id = p.user_id
JOIN challenge_dex_slot s ON s.challenge_dex_id = p.challenge_dex_id
WHERE s.slot_order < CASE WHEN su.g <= 8 THEN 10 WHEN su.g = 9 THEN 6 ELSE 3 END;

-- 6) 음식 리뷰 (해금한 모든 슬롯) --------------------------------------------
INSERT INTO review (reviewer_id, review_type, challenge_dex_id, slot_id, content, rating, like_count, created_at)
SELECT p.user_id, 'FOOD', p.challenge_dex_id, s.id,
       format(
           (ARRAY[
               '%1$s %2$s 정말 맛있었어요. 재방문 각!',
               '기대 이상이었어요. %2$s 강추합니다.',
               '%2$s 먹으러 또 올 것 같아요. (%1$s)',
               '분위기도 좋고 %2$s도 훌륭했어요.',
               '웨이팅은 있었지만 %1$s 만족스러웠어요.'
           ])[1 + ((su.g * 3 + s.slot_order) % 5)],
           s.store_name, s.food_name
       ),
       3 + ((su.g + s.slot_order) % 3),
       0,
       now() - interval '30 days' + make_interval(days => su.g, hours => s.slot_order + 1)
FROM challenge_unlock u
JOIN challenge_participant p ON p.id = u.challenge_participant_id
JOIN challenge_dex_slot s ON s.id = u.slot_id
JOIN (
    SELECT u2.id AS user_id,
           ((cast(substring(u2.provider_id from 'seedcc_p([0-9]+)') AS int) - 1) % 10) + 1 AS g
    FROM users u2
    WHERE u2.provider = 'KAKAO' AND u2.provider_id LIKE 'seedcc_p%'
) su ON su.user_id = p.user_id;

-- 7) 챌린지 리뷰 (완주자 8명) ------------------------------------------------
INSERT INTO review (reviewer_id, review_type, challenge_dex_id, slot_id, content, rating, like_count, created_at)
SELECT p.user_id, 'CHALLENGE', p.challenge_dex_id, NULL,
       format(
           (ARRAY[
               '%1$s 완주 성공! 코스 구성이 알차서 즐거웠어요.',
               '%1$s 도장깨기 끝냈습니다. 다음 챌린지도 기대돼요!',
               '%1$s 덕분에 안 가본 맛집 다 도장 찍었네요. 강추!',
               '%1$s 완주 인증! 난이도 적당하고 리스트가 좋아요.'
           ])[1 + (su.g % 4)],
           c.name
       ),
       4 + (su.g % 2),
       0,
       now() - interval '30 days' + interval '10 days' + make_interval(hours => su.g + 1)
FROM challenge_participant p
JOIN (
    SELECT u.id AS user_id,
           ((cast(substring(u.provider_id from 'seedcc_p([0-9]+)') AS int) - 1) % 10) + 1 AS g
    FROM users u
    WHERE u.provider = 'KAKAO' AND u.provider_id LIKE 'seedcc_p%'
) su ON su.user_id = p.user_id
JOIN challenge_dex c ON c.id = p.challenge_dex_id
WHERE su.g <= 8;

-- 8) 좋아요: 각 리뷰마다 같은 챌린지의 다른 참여자 0~3명 ----------------------
INSERT INTO review_like (review_id, user_id, created_at)
SELECT r.id, x.user_id, now()
FROM review r
JOIN LATERAL (
    SELECT p.user_id, row_number() OVER (ORDER BY p.user_id) AS rn
    FROM challenge_participant p
    WHERE p.challenge_dex_id = r.challenge_dex_id AND p.user_id <> r.reviewer_id
) x ON x.rn <= (r.id % 4)
WHERE r.reviewer_id IN (SELECT id FROM users WHERE provider = 'KAKAO' AND provider_id LIKE 'seedcc_%');

-- 비정규화 카운터 동기화
UPDATE review r
   SET like_count = (SELECT count(*) FROM review_like l WHERE l.review_id = r.id)
 WHERE r.reviewer_id IN (SELECT id FROM users WHERE provider = 'KAKAO' AND provider_id LIKE 'seedcc_%');

-- 9) 확인용 요약 -------------------------------------------------------------
SELECT c.id, c.name,
       (SELECT count(*) FROM challenge_dex_slot s WHERE s.challenge_dex_id = c.id)                                   AS slots,
       (SELECT count(*) FROM challenge_participant p WHERE p.challenge_dex_id = c.id)                                AS participants,
       (SELECT count(*) FROM challenge_participant p WHERE p.challenge_dex_id = c.id AND p.completed_at IS NOT NULL)  AS completed,
       (SELECT count(*) FROM review r WHERE r.challenge_dex_id = c.id AND r.review_type = 'FOOD')                    AS food_reviews,
       (SELECT count(*) FROM review r WHERE r.challenge_dex_id = c.id AND r.review_type = 'CHALLENGE')               AS challenge_reviews
FROM challenge_dex c
WHERE c.owner_id = (SELECT id FROM users WHERE provider = 'KAKAO' AND provider_id = 'seedcc_owner')
ORDER BY c.id;
