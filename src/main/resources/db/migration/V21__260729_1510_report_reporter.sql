-- 제보자 추적: 누가 제보했는지 기록
ALTER TABLE unidentified_food_reports ADD COLUMN reporter_id BIGINT;
ALTER TABLE unidentified_food_reports
    ADD CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users (id);