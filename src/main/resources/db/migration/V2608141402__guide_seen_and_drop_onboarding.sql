-- 전역 온보딩 → 도메인별 코치마크 전환.
-- 1) 무엇을 봤는지 --------------------------------------------------------
-- 행이 있으면 봤다는 뜻 — 없으면 그 화면 첫 진입에서 자동 재생된다.
--
-- 컬럼(boolean 3개)이 아니라 키-값 테이블인 이유:
-- 가이드는 늘어난다(기록 작성·초대·뱃지 그리기 등). 컬럼 방식이면 그때마다
-- ALTER TABLE이 붙는다.
--
-- seen_at을 남기는 이유:
-- 가이드 문구를 개편했을 때 특정 시각 이전에 본 사람에게만 다시 보여줄 수 있다.
CREATE TABLE user_guide_seen (
    user_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    guide_key VARCHAR(40) NOT NULL,
    seen_at   TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, guide_key)
);

-- 2) 옛 전역 온보딩 플래그 제거
ALTER TABLE users DROP COLUMN onboarding_completed;
