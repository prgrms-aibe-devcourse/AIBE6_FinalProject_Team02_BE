-- 기본(가입) 뱃지 이미지가 나무 수저로 바뀌어 이름도 맞춘다
-- code(SPOON_STEEL)는 내부 식별자라 유지, 표시 이름만 변경
UPDATE badge SET name = '나무 수저' WHERE code = 'SPOON_STEEL';
