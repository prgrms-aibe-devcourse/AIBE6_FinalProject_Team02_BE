-- 기본 도감 200칸 확정 목록 정합 (PLAN.md "📖 기본 도감 200칸 (확정)")
--
-- V4 시드 149칸과 확정 목록의 차이를 맞춘다:
--   표기 정정 6 / 분리 1 / 카테고리 이동 5 / 삭제 7 / 신규 58  →  149 - 7 + 58 = 200
--
-- 표기 정정과 카테고리 이동은 DELETE+INSERT가 아니라 UPDATE로 처리한다.
-- user_collection.slot_id가 basic_dex.id를 참조하므로, id가 바뀌면 이미 수집한 칸의 연결이 끊긴다.

-- 1) 표기 정정 — 확정 목록의 표기가 basic_dex.name의 정본이다.
--    변형 표기(돈가스, 김치찌게 등)는 새 칸을 만들지 말고 별칭 사전으로 흡수한다.
UPDATE basic_dex SET name = '마라샹궈'          WHERE name = '마라상궈';
UPDATE basic_dex SET name = '아귀찜'            WHERE name = '아구찜';
UPDATE basic_dex SET name = '타코야키'          WHERE name = '타코야끼';
UPDATE basic_dex SET name = '분식튀김'          WHERE name = '튀김';
UPDATE basic_dex SET name = '브런치플레이트'     WHERE name = '브런치 플레이트';
UPDATE basic_dex SET name = '아이스크림·젤라또'  WHERE name = '아이스크림, 젤라또';

-- 2) 한 칸에 두 음식이 묶여 있던 것을 분리한다 (멍게해삼 → 멍게 + 해삼).
--    남는 쪽(멍게)은 id를 보존하기 위해 UPDATE, 새로 생기는 해삼은 5)에서 INSERT.
UPDATE basic_dex SET name = '멍게' WHERE name = '멍게해삼';

-- 3) 카테고리 이동 — 확정 목록 기준으로 소속을 바로잡는다.
--    국밥류는 밥이 아니라 국·탕·찌개, 텐동은 튀김이 아니라 밥·죽·한그릇이다.
UPDATE basic_dex SET category = 'SOUP_STEW'
 WHERE name IN ('돼지국밥', '소고기국밥', '순대국밥', '콩나물국밥');
UPDATE basic_dex SET category = 'RICE_DISH' WHERE name = '텐동';

-- 4) 확정 목록에 없는 칸을 삭제한다.
--    대부분 상위 칸과 중복이다 (치즈돈까스↔돈까스, 화덕피자↔피자, 뼈해장국밥↔뼈해장국,
--    서브웨이 샌드위치↔샌드위치, 컵떡볶이↔떡볶이). 내장국밥·라면땅은 확정 목록에서 제외됐다.
--    이미 수집된 칸이면 user_collection FK 때문에 실패한다 — 조용히 지우는 것보다 낫다.
DELETE FROM basic_dex
 WHERE name IN ('내장국밥', '라면땅', '뼈해장국밥', '서브웨이 샌드위치',
                '치즈돈까스', '컵떡볶이', '화덕피자');

-- 5) 신규 58칸
INSERT INTO basic_dex (name, category)
VALUES
    -- 밥·죽·한그릇
    ('충무김밥', 'RICE_DISH'),
    ('유부초밥', 'RICE_DISH'),
    ('주먹밥', 'RICE_DISH'),
    ('김치볶음밥', 'RICE_DISH'),
    ('새우볶음밥', 'RICE_DISH'),
    ('낙지덮밥', 'RICE_DISH'),
    ('제육덮밥', 'RICE_DISH'),
    ('규동', 'RICE_DISH'),
    ('오야코동', 'RICE_DISH'),
    ('도시락', 'RICE_DISH'),
    -- 면
    ('탄탄면', 'NOODLE'),
    ('울면', 'NOODLE'),
    ('기스면', 'NOODLE'),
    ('나가사키짬뽕', 'NOODLE'),
    ('야끼우동', 'NOODLE'),
    ('볶음우동', 'NOODLE'),
    ('쫄면', 'NOODLE'),
    ('비빔쫄면', 'NOODLE'),
    ('메밀국수', 'NOODLE'),
    ('라면', 'NOODLE'),
    ('비빔면', 'NOODLE'),
    -- 국·탕·찌개
    ('닭개장', 'SOUP_STEW'),
    ('토란국', 'SOUP_STEW'),
    ('미역국', 'SOUP_STEW'),
    ('북엇국', 'SOUP_STEW'),
    ('황태해장국', 'SOUP_STEW'),
    ('닭곰탕', 'SOUP_STEW'),
    ('갈비전골', 'SOUP_STEW'),
    -- 고기·구이·볶음
    ('육회', 'MEAT_DISH'),
    ('갈비찜', 'MEAT_DISH'),
    ('돼지불백', 'MEAT_DISH'),
    ('갈매기살', 'MEAT_DISH'),
    ('훈제오리', 'MEAT_DISH'),
    -- 튀김·치킨·까스
    ('새우튀김', 'FRIED_CHICKEN_CUTLET'),
    ('치킨텐더', 'FRIED_CHICKEN_CUTLET'),
    ('닭강정', 'FRIED_CHICKEN_CUTLET'),
    ('오징어튀김', 'FRIED_CHICKEN_CUTLET'),
    ('고로케', 'FRIED_CHICKEN_CUTLET'),
    ('치킨너겟', 'FRIED_CHICKEN_CUTLET'),
    ('핫윙', 'FRIED_CHICKEN_CUTLET'),
    -- 해산물·회
    ('해삼', 'SEAFOOD'),
    ('전복요리', 'SEAFOOD'),
    ('꼬막무침', 'SEAFOOD'),
    ('꽃게탕', 'SEAFOOD'),
    ('해물탕', 'SEAFOOD'),
    ('생선조림', 'SEAFOOD'),
    -- 분식·길거리
    ('떡꼬치', 'STREET_FOOD'),
    ('닭꼬치', 'STREET_FOOD'),
    ('회오리감자', 'STREET_FOOD'),
    ('군고구마', 'STREET_FOOD'),
    ('군밤', 'STREET_FOOD'),
    -- 빵·버거·피자·브런치
    ('핫도그번', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('포카치아', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('바게트', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('치아바타', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('또띠아랩', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('퀘사디아', 'BREAD_BURGER_PIZZA_BRUNCH'),
    -- 디저트·음료
    ('츄러스', 'DESSERT_DRINK');

-- 6) 불변 규칙 검증 — "전체 200칸 고정"(PLAN §기본 도감)은 도메인 불변 규칙이므로
--    마이그레이션이 스스로 확인한다. 어긋나면 배포가 아니라 여기서 멈춰야 한다.
DO $$
DECLARE
    total INT;
    bad_category TEXT;
BEGIN
    SELECT COUNT(*) INTO total FROM basic_dex;
    IF total <> 200 THEN
        RAISE EXCEPTION '기본 도감은 200칸이어야 합니다 (현재 %칸)', total;
    END IF;

    SELECT string_agg(category || '=' || cnt, ', ' ORDER BY category)
      INTO bad_category
      FROM (SELECT category, COUNT(*) AS cnt FROM basic_dex GROUP BY category) c
     WHERE (category, cnt) NOT IN (
        ('RICE_DISH', 25), ('NOODLE', 30), ('SOUP_STEW', 30),
        ('MEAT_DISH', 25), ('FRIED_CHICKEN_CUTLET', 15), ('SEAFOOD', 25),
        ('STREET_FOOD', 20), ('BREAD_BURGER_PIZZA_BRUNCH', 20), ('DESSERT_DRINK', 10)
     );
    IF bad_category IS NOT NULL THEN
        RAISE EXCEPTION '카테고리별 칸 수가 확정 목록과 다릅니다: %', bad_category;
    END IF;
END $$;
