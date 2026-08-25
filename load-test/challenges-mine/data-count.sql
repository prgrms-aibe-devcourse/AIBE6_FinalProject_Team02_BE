-- run.ps1이 사전 점검에서 실행한다. **한 줄, 숫자 하나**만 돌려줄 것.
-- 이 API의 지배 변수 = 리뷰어 계정이 '참여 중(JOINED)'인 챌린지 개수.
select count(*)
from challenge_participant p
join users u on u.id = p.user_id
where u.provider = 'GOOGLE' and u.provider_id = 'reviewer-demo'
  and p.completed_at is null;
