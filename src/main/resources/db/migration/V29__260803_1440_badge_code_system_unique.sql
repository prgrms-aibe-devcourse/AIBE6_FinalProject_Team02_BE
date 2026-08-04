-- 챌린지 보상 프리셋은 개설 시 '복제'되어 code를 공유(같은 프리셋을 여러 챌린지가 사용)
-- 기존 uk_badge_code(전체 UNIQUE)는 복제본끼리 code 충돌 → 시스템 뱃지에만 유니크 적용하도록 완화
-- 시스템 뱃지(is_system=TRUE) code는 여전히 유일, 커스텀/복제(is_system=FALSE)는 code 자유(NULL 또는 프리셋코드 공유)
ALTER TABLE badge DROP CONSTRAINT uk_badge_code;
CREATE UNIQUE INDEX uk_badge_code ON badge (code) WHERE is_system = TRUE;
