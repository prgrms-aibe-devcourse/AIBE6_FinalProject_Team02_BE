-- 시딩한 더미 챌린지만 골라서 제거 (⚠️ 로컬 전용)
--
--   docker exec -i catcheat-postgres psql -U catcheat -d catcheat_dev < load-test/challenge-ranking/seed/clean-challenges.sql
--
-- 이름이 'LOADTEST-'로 시작하는 것만 지운다. 진짜 데이터는 건드리지 않는다.
-- 자식 → 부모 순서로 지워야 FK에 안 걸린다.

begin;

delete from challenge_unlock
where challenge_participant_id in (
    select p.id from challenge_participant p
    join challenge_dex c on c.id = p.challenge_dex_id
    where c.name like 'LOADTEST-%'
);

delete from challenge_participant
where challenge_dex_id in (select id from challenge_dex where name like 'LOADTEST-%');

delete from challenge_view_daily
where challenge_dex_id in (select id from challenge_dex where name like 'LOADTEST-%');

delete from challenge_dex where name like 'LOADTEST-%';

commit;

select 'challenge_dex(남은 전체)' as 항목, count(*) as 건수 from challenge_dex
union all
select 'LOADTEST 잔여', count(*) from challenge_dex where name like 'LOADTEST-%';
