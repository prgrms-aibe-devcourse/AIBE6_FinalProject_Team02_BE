-- ============================================================
-- 뱃지 마스터 데이터 설계: 지급조건 스키마 + 시스템 뱃지 시드
-- 이미지는 DB에 안 담고 code로 FE가 정적 에셋 매핑
-- 챌린지 커스텀 뱃지는 개설 시 동적 생성(is_system=FALSE, code NULL) → 여기선 시스템 뱃지만 시드
-- ============================================================

ALTER TABLE badge ADD COLUMN code VARCHAR(64);
ALTER TABLE badge ADD COLUMN condition_type VARCHAR(30);   -- SIGNUP / COLLECTION_RATE / CATEGORY_COMPLETE / FIRST_MADE_DEX / CHALLENGE_PRESET
ALTER TABLE badge ADD COLUMN condition_value VARCHAR(64);  -- COLLECTION_RATE=임계 퍼센트, CATEGORY_COMPLETE=basic_dex.category, 그 외 NULL
ALTER TABLE badge ADD COLUMN description VARCHAR(200);
ALTER TABLE badge ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE badge ADD COLUMN display_order INT NOT NULL DEFAULT 0;

-- code는 유니크(챌린지 커스텀은 NULL → 다중 NULL 허용됨)
ALTER TABLE badge ADD CONSTRAINT uk_badge_code UNIQUE (code);


-- ===== 시스템 뱃지 시드 (총 18) =====
INSERT INTO badge (name, code, condition_type, condition_value, description, is_system, display_order) VALUES
-- 가입
('쇠숟가락',   'SPOON_STEEL',   'SIGNUP',          NULL,  '가입 시 지급',        TRUE, 0),
-- 수저 티어 (기본 도감 수집률)
('동수저',     'SPOON_BRONZE',  'COLLECTION_RATE', '25',  '기본 도감 25% 수집',  TRUE, 1),
('은수저',     'SPOON_SILVER',  'COLLECTION_RATE', '50',  '기본 도감 50% 수집',  TRUE, 2),
('금수저',     'SPOON_GOLD',    'COLLECTION_RATE', '75',  '기본 도감 75% 수집',  TRUE, 3),
('다이아수저', 'SPOON_DIAMOND', 'COLLECTION_RATE', '100', '기본 도감 100% 수집', TRUE, 4),
-- 카테고리 완전수집 (뱃지 1 : basic_dex.category 1)
('한국인은 역시 밥심',                    'CATEGORY_RICE_DISH', 'CATEGORY_COMPLETE', 'RICE_DISH',                '밥·죽·한 그릇 카테고리 전량 수집', TRUE, 10),
('낮말은 새가 듣고 밥말은 라면 먹고 싶다', 'CATEGORY_NOODLE',    'CATEGORY_COMPLETE', 'NOODLE',                   '면 카테고리 전량 수집',           TRUE, 11),
('가는 날이 장날에 국밥',                  'CATEGORY_SOUP_STEW', 'CATEGORY_COMPLETE', 'SOUP_STEW',                '국·탕·찌개 카테고리 전량 수집',   TRUE, 12),
('저기압일 땐 고기 앞으로',                'CATEGORY_MEAT_DISH', 'CATEGORY_COMPLETE', 'MEAT_DISH',                '고기 구이·볶음 카테고리 전량 수집', TRUE, 13),
('튀기면 운동화도 맛있다',                 'CATEGORY_FRIED',     'CATEGORY_COMPLETE', 'FRIED_CHICKEN_CUTLET',     '튀김·치킨·까스 카테고리 전량 수집', TRUE, 14),
('Under the Sea',                          'CATEGORY_SEAFOOD',   'CATEGORY_COMPLETE', 'SEAFOOD',                  '해산물·회 카테고리 전량 수집',    TRUE, 15),
('잘못했지? 순 대!',                       'CATEGORY_STREET',    'CATEGORY_COMPLETE', 'STREET_FOOD',              '분식·길거리 카테고리 전량 수집',  TRUE, 16),
('마트에서 빠는 팡',                       'CATEGORY_BREAD',     'CATEGORY_COMPLETE', 'BREAD_BURGER_PIZZA_BRUNCH','빵·버거·피자·브런치 카테고리 전량 수집', TRUE, 17),
('디저트 배는 따로 있어',                  'CATEGORY_DESSERT',   'CATEGORY_COMPLETE', 'DESSERT_DRINK',            '디저트·음료 카테고리 전량 수집',  TRUE, 18),
-- 첫 제작 도감 개설
('첫 만남은 너무 어려워', 'FIRST_MADE_DEX', 'FIRST_MADE_DEX', NULL, '첫 제작 도감 개설 시 지급', TRUE, 20),
-- 챌린지 보상 프리셋 (개설 시 복제해서 커스텀. 이름만 변경 가능)
('맛집 탐험가',   'CHALLENGE_PRESET_EXPLORER', 'CHALLENGE_PRESET', NULL, '챌린지 개설 보상 프리셋', TRUE, 30),
('챌린지 완주자', 'CHALLENGE_PRESET_FINISHER', 'CHALLENGE_PRESET', NULL, '챌린지 개설 보상 프리셋', TRUE, 31),
('동네 개척자',   'CHALLENGE_PRESET_PIONEER',  'CHALLENGE_PRESET', NULL, '챌린지 개설 보상 프리셋', TRUE, 32);
