-- 부하테스트용 더미 챌린지 시딩 (⚠️ 로컬 전용 — 운영 DB에서 절대 실행 금지)
--
--   docker exec -i catcheat-postgres psql -U catcheat -d catcheat_dev -v n=500 < load-test/challenge-ranking/seed/seed-challenges.sql
--
-- 여러 번 실행하면 그만큼 **누적**된다 (500 → 다시 4500 = 5000).
--
-- ## 왜 필요한가
--
-- 랭킹 조회는 페이지가 10개여도 내부에서는 **진행중 챌린지 전체**를 훑는다
-- (ChallengeService.getChallenges → findOngoing → in(:전체 id) 집계 → 메모리 정렬).
-- 그래서 이 API의 응답시간은 VU 수보다 **진행중 챌린지 개수 N**에 더 좌우된다.
--
-- 데이터가 3개뿐인 DB에 VU 200을 때려 봐야 "빠르다"만 나오고, 그건
-- Redis ZSET이 필요하다는 근거가 전혀 되지 않는다. N을 바꿔 가며 재야 곡선이 보인다.
--
-- ## 만드는 것
--
--   challenge_dex         N건       이름 'LOADTEST-<배치>-<순번>' — 나중에 골라서 지운다
--   challenge_view_daily  N×7건     VIEWS 정렬 점수용 (최근 7일)
--   challenge_participant N×0~4건   PARTICIPANTS 정렬 점수용
--
-- challenge_unlock은 넣지 않는다 — 슬롯 FK가 얽혀 시딩이 커진다.
-- UNLOCKS 정렬도 in(:전체 id) 조인은 그대로 도니 N 스케일 효과는 측정된다(점수만 0).
--
-- ## 이번 배치만 건드린다
--
-- 자식 행(view_daily·participant)을 name like 'LOADTEST-%'로 고르면 **이전 배치까지 다시 잡아**
-- 유니크 충돌로 통째 롤백된다. 그래서 방금 insert한 id만 임시 테이블에 받아서 쓴다.

-- -v n=<개수> 를 안 주면 500으로 본다
\if :{?n}
\else
\set n 500
\endif

begin;

-- 이번 실행을 구분하는 배치 태그 (이름 충돌 방지)
create temp table _batch on commit drop as
select to_char(clock_timestamp(), 'MMDDHH24MISS') as tag;

-- 1) 챌린지 본체 — 방금 만든 id만 따로 받아 둔다
create temp table _new_dex on commit drop as
with ins as (
    insert into challenge_dex (owner_id, name, description, period_type, starts_at, ends_at, is_event, created_at)
    select
        (select id from users order by id limit 1),
        'LOADTEST-' || (select tag from _batch) || '-' || g,
        '부하테스트용 더미 챌린지',
        'PERMANENT',
        now() - interval '1 day',
        null,
        false,
        -- created_at을 흩어 둔다. 전부 같으면 안정 정렬 동작이 가려진다
        now() - (g || ' minutes')::interval
    from generate_series(1, :n) g
    returning id
)
select id from ins;

-- 2) 조회수 (VIEWS 정렬 점수) — 최근 7일치
insert into challenge_view_daily (challenge_dex_id, view_date, view_count)
select d.id, (current_date - s), (random() * 500)::bigint
from _new_dex d
cross join generate_series(0, 6) s;

-- 3) 참여자 (PARTICIPANTS 정렬 점수) — 챌린지당 0~4명, 기존 유저를 재사용
insert into challenge_participant (challenge_dex_id, user_id, joined_at)
select d.id, u.id, now() - (random() * 6 || ' days')::interval
from _new_dex d
join lateral (
    select id from users order by random() limit (random() * 4)::int
) u on true
on conflict do nothing;

commit;

-- 결과 확인
select 'challenge_dex(진행중)' as 항목, count(*) as 건수
from challenge_dex
where is_event = false
  and starts_at <= now() and (ends_at is null or ends_at > now())
union all
select 'challenge_view_daily', count(*) from challenge_view_daily
union all
select 'challenge_participant', count(*) from challenge_participant;
