-- 시딩한 더미 피드 그룹과 더미 유저를 제거 (⚠️ 로컬 전용)
--
--   docker exec -i catcheat-postgres psql -U catcheat -d catcheat_dev < load-test/logit-feed/seed/clean-feed.sql
--
-- 이름이 'LOADTEST-FEED'로 시작하는 그룹과 provider_id가 'loadtest-feed-'로 시작하는
-- 유저만 지운다. 진짜 데이터와 리뷰어 계정(reviewer-demo)은 건드리지 않는다.
--
-- seed-feed.sql이 실행 첫머리에 같은 일을 하므로, 크기를 바꿔 다시 잴 때는
-- 이 파일을 따로 돌릴 필요가 없다. 측정을 끝내고 로컬을 되돌릴 때 쓴다.
--
-- 자식 → 부모 순서로 지워야 FK에 안 걸린다.
-- made_dex_comment·_like 계열은 ON DELETE CASCADE가 없어 직접 지운다.

begin;

create temp table _old_dex on commit drop as
select id from made_dex where name like 'LOADTEST-FEED%';

create temp table _old_record on commit drop as
select id from made_dex_record where made_dex_id in (select id from _old_dex);

delete from made_dex_comment_like
where comment_id in (select id from made_dex_comment
                     where made_dex_record_id in (select id from _old_record));
delete from made_dex_comment
where made_dex_record_id in (select id from _old_record);
delete from made_dex_record_like
where record_id in (select id from _old_record);
delete from made_dex_record_photo
where record_id in (select id from _old_record);
-- made_dex_record_food 는 V2608111500(로그잇 정리)에서 DROP TABLE 됐다
delete from made_dex_record where id in (select id from _old_record);
delete from made_dex_slot   where made_dex_id in (select id from _old_dex);
delete from made_dex_member where made_dex_id in (select id from _old_dex);
delete from made_dex_invite where made_dex_id in (select id from _old_dex);
delete from made_dex        where id in (select id from _old_dex);

delete from users where provider = 'GOOGLE' and provider_id like 'loadtest-feed-%';

commit;

select 'LOADTEST-FEED 그룹 잔여' as 항목, count(*) as 건수
from made_dex where name like 'LOADTEST-FEED%'
union all
select '더미 유저 잔여', count(*)
from users where provider = 'GOOGLE' and provider_id like 'loadtest-feed-%'
union all
select 'made_dex 전체(남은 것)', count(*) from made_dex;
