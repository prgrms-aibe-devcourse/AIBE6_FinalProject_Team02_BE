-- run.ps1이 사전 점검에서 실행한다. **한 줄, 숫자 하나**만 돌려줄 것.
--
-- 이 API의 성능을 지배하는 변수를 센다. 챌린짓 랭킹은 VU가 아니라
-- "진행중 챌린지 개수 N"이 응답시간을 좌우한다.
-- 도메인마다 이 값이 다르다 (업로드=파일 크기, 목록=행 수, 쓰기=경쟁 정도).
--
-- 이 파일이 없으면 run.ps1은 데이터량 점검을 건너뛴다.
select count(*)
from challenge_dex
where deleted_at is null
  and is_event = false
  and starts_at <= now()
  and (ends_at is null or ends_at > now());
