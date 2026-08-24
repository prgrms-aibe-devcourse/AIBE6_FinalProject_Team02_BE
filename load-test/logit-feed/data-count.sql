-- floor: 10
-- seed: -v m=12 -v s=4 -v p=1
-- unit: 요청당 presign 서명 횟수
--
-- 위 세 줄은 실행기가 읽는 선언이다 (선택 사항).
--   floor — 이 값 미만일 때만 "데이터가 적다" 경고를 낸다.
--           기본값 100은 "행 수"를 세는 도메인 기준이라 여기엔 안 맞는다.
--           이 API의 서명 횟수는 M+M×S라 그룹 정원(12) · 슬롯 4개면 60이 사실상 상한이다.
--           10 = 멤버 2명짜리 최소 구성. 그 아래면 시드가 안 들어간 것이다.
--   seed  — 경고와 함께 안내할 시드 인자. 이 시드는 n이 아니라 m·s·p를 받는다.
--   unit  — 숫자가 무엇의 개수인지. 콘솔과 결과 파일 헤더에 함께 찍힌다.
--
-- run.ps1 / run.sh 가 사전 점검에서 실행한다. **한 줄, 숫자 하나**만 돌려줄 것.
--
-- 이 API의 성능을 지배하는 변수를 센다. 로그잇 피드는 VU가 아니라
-- **요청 1건이 만들어 내는 presigned URL 서명 횟수**가 응답시간을 좌우한다.
--
--   서명 횟수 = 프로필이 있는 멤버 수 M
--             + 오늘 기록이 있는 (슬롯 × 작성자) 칸 수      ≤ M + M×S
--
-- 왜 "사진 총 개수"가 아니라 이 값인가 —
-- MadeDexFeedService.toCard()는 카드 1장에 **대표 사진 1장만** 서명한다.
-- 기록당 사진을 8장 넣어도 서명은 카드당 1회다. 그래서 사진 총 개수를 세면
-- 실제 비용보다 부풀려진 숫자가 나와 개선 전후 비교가 어긋난다.
--
-- profile_image_key가 null인 멤버는 createDownloadUrl이 서명 없이 바로 null을
-- 반환하므로 세지 않는다.

select
    coalesce((
        select count(*)
          from made_dex_member mm
          join users u on u.id = mm.user_id
         where mm.made_dex_id = (select id from made_dex
                                  where name like 'LOADTEST-FEED%' and deleted_at is null
                                  order by id desc limit 1)
           and u.profile_image_key is not null
    ), 0)
  + coalesce((
        select count(*) from (
            select distinct r.slot_id, r.author_id
              from made_dex_record r
             where r.made_dex_id = (select id from made_dex
                                     where name like 'LOADTEST-FEED%' and deleted_at is null
                                     order by id desc limit 1)
               and r.logged_on = (now() at time zone 'Asia/Seoul')::date
               and r.deleted_at is null
        ) x
    ), 0);
