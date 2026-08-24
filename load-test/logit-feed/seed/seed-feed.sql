-- 로그잇 식탁 피드 부하테스트용 시딩 (⚠️ 로컬 전용 — 개발/운영 DB에서 절대 실행 금지)
--
--   docker exec -i catcheat-postgres psql -U catcheat -d catcheat_dev -v m=12 -v s=4 -v p=1 < load-test/logit-feed/seed/seed-feed.sql
--
--   m = 멤버 수 M (리뷰어 포함, 기본 12)   s = 슬롯 수 S (기본 4)   p = 기록당 사진 수 (기본 1)
--
-- ## 왜 필요한가
--
-- 피드 조회(MadeDexFeedService.findFeed)는 쿼리가 **M·S와 무관하게 6회로 고정**돼 있고
-- 인덱스도 idx_made_dex_record_feed (made_dex_id, logged_on) 이 걸려 있다.
-- 그런데 응답을 만들 때 이미지 1건마다 S3PresignedUrlService.createDownloadUrl() 이
-- SigV4 서명을 **새로** 만든다. 캐시가 없어 같은 키도 매 요청 다시 서명한다.
--
--   서명 횟수 = M (멤버 프로필)  +  기록이 있는 (슬롯 × 멤버) 칸 수  ≤ M + M×S
--
-- 그래서 이 API의 비용은 쿼리 수가 아니라 **응답 안의 이미지 개수**에 붙는다.
-- 데이터가 3건인 로컬에 VU를 아무리 때려도 이 기울기는 안 보인다.
--
-- ## 누적이 아니라 "정확히 그 크기로 재생성"한다
--
-- 챌린지 시드는 여러 번 돌리면 누적되지만, 피드는 M을 바꿔 가며 기울기를 재는 것이
-- 목적이라 크기가 정확해야 한다. 그래서 이전 LOADTEST-FEED 그룹과 더미 유저를
-- 먼저 지우고 새로 만든다. 같은 명령을 M만 바꿔 다시 돌리면 된다.
--
-- ## 만드는 것
--
--   users                  M-1건    provider_id 'loadtest-feed-<n>' — 이것만 골라 지운다
--   made_dex               1건      이름 'LOADTEST-FEED'
--   made_dex_member        M건      리뷰어가 OWNER, 나머지 MEMBER
--   made_dex_slot          S건
--   made_dex_record        M×S건    logged_on = 오늘 (피드 기본 날짜)
--   made_dex_record_photo  M×S×p건  첫 장만 카드 썸네일로 서명된다
--
-- 리뷰어 계정(GOOGLE/reviewer-demo)이 없으면 여기서 만든다.
-- test-login을 먼저 부르지 않아 유저가 없으면 FK NOT NULL로 **전체가 조용히 롤백**된다.

\if :{?m}
\else
\set m 12
\endif
\if :{?s}
\else
\set s 4
\endif
\if :{?p}
\else
\set p 1
\endif

begin;

-- ── 1) 이전 배치 제거 ──────────────────────────────────────────────
-- 자식 → 부모 순서. comment·like 계열은 ON DELETE CASCADE가 없어 직접 지운다.

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
-- made_dex_record_food 는 V2608111500(로그잇 정리)에서 DROP TABLE 됐다.
-- V33의 DDL만 보고 쓰면 여기서 전체가 롤백된다
delete from made_dex_record where id in (select id from _old_record);
delete from made_dex_slot   where made_dex_id in (select id from _old_dex);
delete from made_dex_member where made_dex_id in (select id from _old_dex);
delete from made_dex_invite where made_dex_id in (select id from _old_dex);
delete from made_dex        where id in (select id from _old_dex);

-- 더미 유저는 이 시드만 만든다. 리뷰어(reviewer-demo)는 건드리지 않는다
delete from users where provider = 'GOOGLE' and provider_id like 'loadtest-feed-%';

-- ── 2) 리뷰어 계정 확보 ────────────────────────────────────────────
-- test-login이 find-or-create로 만드는 계정과 같은 자연키를 쓴다

insert into users (provider, provider_id, nickname, email, role, profile_image_key, created_at)
values ('GOOGLE', 'reviewer-demo', '심사리뷰어', 'reviewer@catcheat.test', 'ADMIN',
        'uploads/loadtest/profile-reviewer.jpg', now())
on conflict (provider, provider_id) do nothing;

create temp table _owner on commit drop as
select id from users where provider = 'GOOGLE' and provider_id = 'reviewer-demo';

-- 리뷰어가 예전에 프로필 없이 만들어졌으면 채운다.
-- profile_image_key가 null이면 createDownloadUrl이 서명 없이 바로 null을 반환해
-- 멤버 프로필 M건이 측정에서 통째로 빠진다
update users set profile_image_key = 'uploads/loadtest/profile-reviewer.jpg'
where id = (select id from _owner) and profile_image_key is null;

-- ── 3) 더미 멤버 M-1명 ─────────────────────────────────────────────

create temp table _member on commit drop as
with ins as (
    insert into users (provider, provider_id, nickname, email, role, profile_image_key, created_at)
    select 'GOOGLE',
           'loadtest-feed-' || g,
           'LT피드' || g,
           'lt-feed-' || g || '@catcheat.test',
           'USER',
           'uploads/loadtest/profile-' || g || '.jpg',
           now()
    from generate_series(1, :m - 1) g
    returning id
)
select id from ins;

-- ── 4) 그룹 · 멤버 · 슬롯 ──────────────────────────────────────────

create temp table _dex on commit drop as
with ins as (
    -- visibility 도 V2608111500에서 드롭됐다 (로그잇은 비공개 전용이라 컬럼 자체가 사라짐)
    insert into made_dex (owner_id, name, description, max_members, created_at)
    select (select id from _owner),
           'LOADTEST-FEED',
           '부하테스트용 식탁 피드 (M=' || :m || ', S=' || :s || ', p=' || :p || ')',
           greatest(:m, 12),
           now()
    returning id
)
select id from ins;

-- 리뷰어를 가장 먼저 넣는다. 피드는 joined_at 순으로 카드를 세운다
insert into made_dex_member (made_dex_id, user_id, role, joined_at)
select (select id from _dex), (select id from _owner), 'OWNER', now() - interval '1 hour';

insert into made_dex_member (made_dex_id, user_id, role, joined_at)
select (select id from _dex), m.id, 'MEMBER',
       now() - interval '1 hour' + (row_number() over (order by m.id) || ' second')::interval
from _member m;

create temp table _slot on commit drop as
with ins as (
    insert into made_dex_slot (made_dex_id, name, sort_order, created_at)
    select (select id from _dex), '끼니' || g, g - 1, now()
    from generate_series(1, :s) g
    returning id, sort_order
)
select id, sort_order from ins;

-- ── 5) 기록 — 멤버 × 슬롯 전부 채운다 ──────────────────────────────
-- 빈 칸이 있으면 그 칸은 서명이 돌지 않아 M×S가 곧 서명 횟수가 되지 않는다.
-- 최대 부하 조건을 만들기 위해 전부 채운다
--
-- ⚠️ current_date 를 쓰면 안 된다.
-- postgres 컨테이너는 UTC라 한국 시각 00:00~09:00 사이에는 current_date가 **어제**다.
-- 반면 앱은 TimeConfig.SERVICE_ZONE(Asia/Seoul) 기준으로 오늘을 고른다.
-- 그러면 기록이 전부 "어제"로 들어가 피드는 200을 주면서 카드가 **전부 빈 칸**이 된다
-- (썸네일 서명이 한 번도 안 돌아 측정 대상 자체가 사라진다).
-- 실제로 첫 스모크에서 checks 66.66%로 이걸 밟았다.

create temp table _record on commit drop as
with all_member as (
    select id from _owner
    union all
    select id from _member
),
ins as (
    insert into made_dex_record (made_dex_id, slot_id, author_id, logged_on, logged_at, created_at)
    select (select id from _dex), s.id, u.id, (now() at time zone 'Asia/Seoul')::date,
           (now() at time zone 'Asia/Seoul')::date + (s.sort_order || ' hour')::interval + interval '8 hour',
           now()
    from _slot s
    cross join all_member u
    returning id
)
select id from ins;

-- ── 6) 사진 — 기록당 p장 ───────────────────────────────────────────
-- 카드 썸네일로 서명되는 건 sort_order = 0 인 첫 장뿐이다.
-- 나머지는 조회되는 행 수만 늘린다 (쿼리 비용과 서명 비용을 갈라 보기 위한 축)

insert into made_dex_record_photo (record_id, image_key, sort_order, crop_x, crop_y)
select r.id,
       'uploads/loadtest/feed-' || r.id || '-' || g || '.jpg',
       g - 1,
       50, 50
from _record r
cross join generate_series(1, :p) g;

commit;

-- ── 결과 확인 ──────────────────────────────────────────────────────
-- INSERT 건수가 찍혀도 롤백되면 데이터는 그대로다. 반드시 다시 센다

select '멤버 M' as 항목, count(*) as 건수
from made_dex_member
where made_dex_id = (select id from made_dex where name = 'LOADTEST-FEED')
union all
select '슬롯 S', count(*)
from made_dex_slot
where made_dex_id = (select id from made_dex where name = 'LOADTEST-FEED')
union all
select '오늘 기록 M×S', count(*)
from made_dex_record
where made_dex_id = (select id from made_dex where name = 'LOADTEST-FEED')
  and logged_on = (now() at time zone 'Asia/Seoul')::date
union all
select '사진', count(*)
from made_dex_record_photo
where record_id in (select id from made_dex_record
                    where made_dex_id = (select id from made_dex where name = 'LOADTEST-FEED'))
union all
select '★ 요청당 서명 횟수',
       (select count(*) from made_dex_member mm
          join users u on u.id = mm.user_id
         where mm.made_dex_id = (select id from made_dex where name = 'LOADTEST-FEED')
           and u.profile_image_key is not null)
     + (select count(*) from (
           select distinct r.slot_id, r.author_id
             from made_dex_record r
            where r.made_dex_id = (select id from made_dex where name = 'LOADTEST-FEED')
              and r.logged_on = (now() at time zone 'Asia/Seoul')::date
              and r.deleted_at is null) x);
