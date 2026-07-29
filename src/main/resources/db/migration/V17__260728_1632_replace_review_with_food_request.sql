-- 팀원 리뷰 큐(review_queue_item)를 우리 음식 등록 요청 큐(food_registration_requests)로 교체한다.
--
-- 배경: "AI가 등록을 못 끝냄 → 관리자 검토 → 수락 시 도감 칸 열림" 흐름을
--       admin 도메인의 food_registration_requests 한곳으로 통합한다.
--       (review_queue_item과 이중 구현이던 것을 정리)

-- 1) 등록 요청이 어떤 수집 카드/증빙 사진과 엮이는지 참조 추가
--    - complete 시 이 카드를 칸에 붙여 해금한다 (ReviewService.approve가 하던 일)
ALTER TABLE food_registration_requests ADD COLUMN collection_card_id BIGINT;
ALTER TABLE food_registration_requests ADD COLUMN evidence_photo_id BIGINT;

-- 같은 카드가 큐에 두 번 들어가지 않도록 (리뷰 큐의 uk_review_queue_card와 동일 취지)
ALTER TABLE food_registration_requests
    ADD CONSTRAINT uk_frr_collection_card UNIQUE (collection_card_id);
ALTER TABLE food_registration_requests
    ADD CONSTRAINT fk_frr_collection_card
        FOREIGN KEY (collection_card_id) REFERENCES collection_card (id);

-- 대기 목록 상태 조회용 인덱스
CREATE INDEX idx_frr_status ON food_registration_requests (status);

-- 2) 이제 쓰지 않는 리뷰 큐 테이블 제거
--    (생산자 RegistrationConfirmService는 food_registration_requests에 쓰도록 rewire됨)
DROP TABLE IF EXISTS review_queue_item;
