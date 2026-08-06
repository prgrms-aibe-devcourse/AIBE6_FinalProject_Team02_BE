CREATE UNIQUE INDEX IF NOT EXISTS uk_basic_dex_name ON basic_dex (name);

INSERT INTO basic_dex (name, category)
VALUES
    ('비빔밥', 'RICE_DISH'),
    ('돌솥비빔밥', 'RICE_DISH'),
    ('김밥', 'RICE_DISH'),
    ('볶음밥', 'RICE_DISH'),
    ('오므라이스', 'RICE_DISH'),
    ('덮밥', 'RICE_DISH'),
    ('회덮밥', 'RICE_DISH'),
    ('카레라이스', 'RICE_DISH'),
    ('쌈밥', 'RICE_DISH'),
    ('보리밥', 'RICE_DISH'),
    ('솥밥', 'RICE_DISH'),
    ('죽', 'RICE_DISH'),
    ('리소토', 'RICE_DISH'),
    ('컵밥', 'RICE_DISH'),
    ('돼지국밥', 'RICE_DISH'),
    ('소고기국밥', 'RICE_DISH'),
    ('순대국밥', 'RICE_DISH'),
    ('콩나물국밥', 'RICE_DISH'),
    ('내장국밥', 'RICE_DISH'),
    ('뼈해장국밥', 'RICE_DISH'),
    ('물냉면', 'NOODLE'),
    ('비빔냉면', 'NOODLE'),
    ('밀면', 'NOODLE'),
    ('칼국수', 'NOODLE'),
    ('잔치국수', 'NOODLE'),
    ('비빔국수', 'NOODLE'),
    ('콩국수', 'NOODLE'),
    ('막국수', 'NOODLE'),
    ('수제비', 'NOODLE'),
    ('짜장면', 'NOODLE'),
    ('짬뽕', 'NOODLE'),
    ('라멘', 'NOODLE'),
    ('우동', 'NOODLE'),
    ('소바', 'NOODLE'),
    ('파스타', 'NOODLE'),
    ('쌀국수', 'NOODLE'),
    ('팟타이', 'NOODLE'),
    ('마라탕', 'NOODLE'),
    ('냉모밀', 'NOODLE'),
    ('김치찌개', 'SOUP_STEW'),
    ('된장찌개', 'SOUP_STEW'),
    ('순두부찌개', 'SOUP_STEW'),
    ('부대찌개', 'SOUP_STEW'),
    ('청국장', 'SOUP_STEW'),
    ('설렁탕', 'SOUP_STEW'),
    ('곰탕', 'SOUP_STEW'),
    ('갈비탕', 'SOUP_STEW'),
    ('삼계탕', 'SOUP_STEW'),
    ('감자탕', 'SOUP_STEW'),
    ('뼈해장국', 'SOUP_STEW'),
    ('육개장', 'SOUP_STEW'),
    ('추어탕', 'SOUP_STEW'),
    ('매운탕', 'SOUP_STEW'),
    ('알탕', 'SOUP_STEW'),
    ('동태탕', 'SOUP_STEW'),
    ('어묵탕', 'SOUP_STEW'),
    ('샤브샤브', 'SOUP_STEW'),
    ('마라상궈', 'SOUP_STEW'),
    ('삼겹살', 'MEAT_DISH'),
    ('목살', 'MEAT_DISH'),
    ('소갈비', 'MEAT_DISH'),
    ('돼지갈비', 'MEAT_DISH'),
    ('소고기구이', 'MEAT_DISH'),
    ('불고기', 'MEAT_DISH'),
    ('제육볶음', 'MEAT_DISH'),
    ('닭갈비', 'MEAT_DISH'),
    ('찜닭', 'MEAT_DISH'),
    ('족발', 'MEAT_DISH'),
    ('보쌈', 'MEAT_DISH'),
    ('곱창', 'MEAT_DISH'),
    ('대창', 'MEAT_DISH'),
    ('막창', 'MEAT_DISH'),
    ('닭발', 'MEAT_DISH'),
    ('오리고기', 'MEAT_DISH'),
    ('양꼬치', 'MEAT_DISH'),
    ('스테이크', 'MEAT_DISH'),
    ('수육', 'MEAT_DISH'),
    ('LA갈비', 'MEAT_DISH'),
    ('후라이드치킨', 'FRIED_CHICKEN_CUTLET'),
    ('양념치킨', 'FRIED_CHICKEN_CUTLET'),
    ('간장치킨', 'FRIED_CHICKEN_CUTLET'),
    ('돈까스', 'FRIED_CHICKEN_CUTLET'),
    ('치즈돈까스', 'FRIED_CHICKEN_CUTLET'),
    ('생선까스', 'FRIED_CHICKEN_CUTLET'),
    ('탕수육', 'FRIED_CHICKEN_CUTLET'),
    ('깐풍기', 'FRIED_CHICKEN_CUTLET'),
    ('가라아게', 'FRIED_CHICKEN_CUTLET'),
    ('텐동', 'FRIED_CHICKEN_CUTLET'),
    ('광어회', 'SEAFOOD'),
    ('연어회', 'SEAFOOD'),
    ('참치회', 'SEAFOOD'),
    ('초밥', 'SEAFOOD'),
    ('물회', 'SEAFOOD'),
    ('조개구이', 'SEAFOOD'),
    ('대하구이', 'SEAFOOD'),
    ('장어구이', 'SEAFOOD'),
    ('고등어구이', 'SEAFOOD'),
    ('갈치조림', 'SEAFOOD'),
    ('아구찜', 'SEAFOOD'),
    ('해물찜', 'SEAFOOD'),
    ('간장게장', 'SEAFOOD'),
    ('양념게장', 'SEAFOOD'),
    ('낙지볶음', 'SEAFOOD'),
    ('오징어볶음', 'SEAFOOD'),
    ('굴요리', 'SEAFOOD'),
    ('회무침', 'SEAFOOD'),
    ('멍게해삼', 'SEAFOOD'),
    ('떡볶이', 'STREET_FOOD'),
    ('순대', 'STREET_FOOD'),
    ('튀김', 'STREET_FOOD'),
    ('어묵', 'STREET_FOOD'),
    ('김말이', 'STREET_FOOD'),
    ('라볶이', 'STREET_FOOD'),
    ('토스트', 'STREET_FOOD'),
    ('핫도그', 'STREET_FOOD'),
    ('붕어빵', 'STREET_FOOD'),
    ('호떡', 'STREET_FOOD'),
    ('계란빵', 'STREET_FOOD'),
    ('호두과자', 'STREET_FOOD'),
    ('타코야끼', 'STREET_FOOD'),
    ('만두', 'STREET_FOOD'),
    ('찐빵', 'STREET_FOOD'),
    ('컵떡볶이', 'STREET_FOOD'),
    ('라면땅', 'STREET_FOOD'),
    ('햄버거', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('수제버거', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('피자', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('샌드위치', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('베이글', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('크로플', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('크루아상', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('소금빵', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('파니니', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('브런치 플레이트', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('프렌치토스트', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('케밥', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('부리토', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('타코', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('화덕피자', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('서브웨이 샌드위치', 'BREAD_BURGER_PIZZA_BRUNCH'),
    ('빙수', 'DESSERT_DRINK'),
    ('케이크', 'DESSERT_DRINK'),
    ('마카롱', 'DESSERT_DRINK'),
    ('타르트', 'DESSERT_DRINK'),
    ('도넛', 'DESSERT_DRINK'),
    ('아이스크림, 젤라또', 'DESSERT_DRINK'),
    ('전통 디저트(약과·떡)', 'DESSERT_DRINK'),
    ('와플', 'DESSERT_DRINK'),
    ('푸딩', 'DESSERT_DRINK')
ON CONFLICT (name) DO UPDATE
SET category = EXCLUDED.category;

UPDATE basic_dex
SET illustration_url = '국-탕-찌개/' || name || '.png'
WHERE name IN (
    '돼지국밥',
    '소고기국밥',
    '순대국밥',
    '콩나물국밥',
    '내장국밥',
    '뼈해장국밥'
);

UPDATE basic_dex
SET illustration_url = '빵, 버거, 피자, 브런치/' || name || '.png'
WHERE category = 'BREAD_BURGER_PIZZA_BRUNCH';

UPDATE basic_dex
SET illustration_url = '빵, 버거, 피자, 브런치/부리또.png'
WHERE name = '부리토';

UPDATE basic_dex
SET illustration_url = '빵, 버거, 피자, 브런치/브런치플레이트.png'
WHERE name = '브런치 플레이트';

UPDATE basic_dex
SET illustration_url = '국-탕-찌개/마라샹궈.png'
WHERE name = '마라상궈';

UPDATE basic_dex
SET illustration_url = '밥,죽, 한그릇/국밥(뼈해장국).png'
WHERE name = '뼈해장국밥';

UPDATE basic_dex
SET illustration_url = '밥,죽, 한그릇/내장국밥.png'
WHERE name = '내장국밥';

UPDATE basic_dex
SET illustration_url = '해산물/멍게해삼.png'
WHERE name = '멍게해삼';

UPDATE basic_dex
SET illustration_url = '디저트/' || name || '.png'
WHERE category = 'DESSERT_DRINK';

UPDATE basic_dex
SET illustration_url = '디저트/아이스크림, 젤라또.png'
WHERE name = '아이스크림, 젤라또';

UPDATE basic_dex
SET illustration_url = '디저트/전통디저트(약과, 떡).png'
WHERE name = '전통 디저트(약과·떡)';
