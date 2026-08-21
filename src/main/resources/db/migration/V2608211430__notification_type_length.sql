-- FOOD_REGISTRATION_REQUEST_RECEIVED(34자)가 varchar(30)을 넘어서 insert가 매번 실패했다
ALTER TABLE notification ALTER COLUMN type TYPE VARCHAR(50);
