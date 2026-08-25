-- 시드 정리 (⚠️ 로컬 전용). LOADTEST-MINE-% 챌린지와 그 자식 행 삭제.
begin;
create temp table _d on commit drop as select id from challenge_dex where name like 'LOADTEST-MINE-%';
delete from challenge_unlock where challenge_participant_id in
    (select id from challenge_participant where challenge_dex_id in (select id from _d));
delete from challenge_participant where challenge_dex_id in (select id from _d);
delete from challenge_dex_slot where challenge_dex_id in (select id from _d);
delete from challenge_dex where id in (select id from _d);
commit;
