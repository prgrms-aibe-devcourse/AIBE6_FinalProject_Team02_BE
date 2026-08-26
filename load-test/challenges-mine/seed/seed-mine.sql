-- 부하테스트용: 리뷰어 계정이 참여(JOINED)한 더미 챌린지 N개 시딩 (⚠️ 로컬 전용)
--
--   docker exec -i catcheat-postgres psql -U catcheat -d catcheat_dev -v n=500 < load-test/challenges-mine/seed/seed-mine.sql
--
-- ⚠️ 먼저 test-login으로 리뷰어 계정을 한 번 만들어 둬야 한다(users에 provider_id='reviewer-demo' 존재).
-- 여러 번 실행하면 누적된다. 정리는 clean-mine.sql.
--
-- 만드는 것(챌린지당): 슬롯 5개 + 리뷰어 참여 1건(completed_at null=진행중) + 해금 2건.
-- 이래야 getMyChallenges의 건별 조회(슬롯 count·참여·해금 count) N+1이 실제로 돈다.
--
-- 스키마 주의(V23 + 이후 마이그레이션 반영):
--   challenge_dex        : created_at 있음(그 외 감사컬럼은 updated_at nullable)
--   challenge_dex_slot   : created_at 없음
--   challenge_participant: created_at 없음(joined_at/completed_at만)
--   challenge_unlock     : created_at 없음(unlocked_at만)

\set ON_ERROR_STOP on

\if :{?n}
\else
\set n 500
\endif

begin;

-- 리뷰어 id (없으면 이후 insert가 0건 → 아래 \echo 카운트로 확인)
create temp table _rv on commit drop as
select id as uid from users where provider = 'GOOGLE' and provider_id = 'reviewer-demo';

\echo '--- 리뷰어 계정 수(1 이어야 정상):'
select count(*) as reviewer_rows from _rv;

create temp table _b on commit drop as
select to_char(clock_timestamp(), 'MMDDHH24MISS') as tag;

-- 챌린지 N개 (owner는 리뷰어여도 무방 — JOINED 판정은 participant 기준)
create temp table _dex on commit drop as
with ins as (
    insert into challenge_dex(owner_id, name, period_type, starts_at, ends_at, is_event, created_at)
    select (select uid from _rv),
           'LOADTEST-MINE-' || (select tag from _b) || '-' || g,
           'PERMANENT', now(), null, false, now()
    from generate_series(1, :n) g
    where exists (select 1 from _rv)
    returning id
)
select id from ins;

-- 슬롯 5개/챌린지 (created_at 컬럼 없음)
create temp table _slot on commit drop as
with ins as (
    insert into challenge_dex_slot(challenge_dex_id, food_name, slot_order)
    select d.id, 'food-' || s, s
    from _dex d cross join generate_series(0, 4) s
    returning id, challenge_dex_id
)
select id, challenge_dex_id from ins;

-- 리뷰어 참여 1건/챌린지 (진행중: completed_at null, created_at 컬럼 없음)
create temp table _part on commit drop as
with ins as (
    insert into challenge_participant(challenge_dex_id, user_id, joined_at, completed_at)
    select d.id, (select uid from _rv), now(), null
    from _dex d
    returning id, challenge_dex_id
)
select id, challenge_dex_id from ins;

-- 해금 2건/참여 (그 챌린지 슬롯 중 2개, created_at 컬럼 없음)
insert into challenge_unlock(challenge_participant_id, slot_id, unlocked_at)
select p.id, s.id, now()
from _part p
join lateral (
    select id from _slot s where s.challenge_dex_id = p.challenge_dex_id order by id limit 2
) s on true;

\echo '--- 이번에 삽입된 건수(dex / slot / participant):'
select (select count(*) from _dex)  as dex,
       (select count(*) from _slot) as slot,
       (select count(*) from _part) as participant;

commit;

\echo '시드 완료. data-count.sql로 참여 챌린지 개수를 다시 확인할 것(시드 롤백 감지).'
