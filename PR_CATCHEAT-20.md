## 📌 관련 이슈

Jira: CATCHEAT-20 (EPIC B — 챌린지 도감)

## ✨ 작업 내용

챌린지 도감의 **개설 · 개설권 · 자유 참여**까지 백엔드를 구현했습니다. 스키마(V23)는 이미 있어, 그 위에 도메인 코드(`domain/challenge`)를 올리는 작업입니다.

- **챌린지 개설** (유형 FIRST_COME/COLLECTION, 기한 PERMANENT/LIMITED, 고정 목표 5개 이상)
- **개설권(월 3회)** — 티켓 테이블 대신 User 카운트 + 월 리필로 구현
- **자유 참여** — 종료·중복 참여 방지

## 커밋

| 커밋 | 내용 |
|---|---|
| `b0056b1`·`b64f5ae` feat | 엔티티 작성 |
| `f11a078` feat | Repository 작성 |
| `73ac633` feat | 개설권(ticket) 구조 |
| `3f9dd94` feat | DTO |
| `28b50e6` feat | 서비스 |
| `3afd2b7` feat | 컨트롤러 |
| `2b0d9a4` feat | 참여 플로우 |
| `c752c08` feat | 테스트 코드 |

## API 엔드포인트 (`/api/v1/challenges`, 인증 사용자)

| 메서드 · 경로 | 동작 |
|---|---|
| `GET /creation-tickets` | 이번 달 남은 개설권 조회 |
| `POST /` | 챌린지 개설 (개설권 1장 소진) |
| `POST /{id}/participants` | 자유 참여 |

## 주요 구현

**1. 개설권은 테이블 대신 User 카운트 + 월 리필**
개설권은 "이번 달 몇 개 남았나"만 필요한 단순 카운트라, 행을 쌓지 않고 `users`에 `challenge_ticket_count` + `challenge_ticket_month`(리필한 연월)만 뒀습니다. 조회·사용 시 저장된 월이 현재 월과 다르면 그 자리에서 3개로 리필합니다(별도 스케줄러 없이 lazy). `challenge_ticket` 테이블은 폐기했습니다. → `V24`

**2. 개설 = 검증 → 개설권 소진 → 저장**
요청이 잘못됐는데 티켓만 소진되지 않도록 검증을 먼저 합니다(슬롯 5개 이상, 유형·기한 필수, LIMITED면 종료 시각이 시작 이후). 저장 중 실패해도 `@Transactional` 롤백으로 티켓까지 원복됩니다.

**3. 기한은 개설 후 변경 불가**
수정 엔드포인트를 아예 만들지 않는 것으로 지킵니다. 상시(PERMANENT)는 종료 시각을 강제로 null 처리해 스키마 규칙을 보장합니다.

**4. 자유 참여**
소프트 삭제된 챌린지는 조회에서 제외(`findByIdAndDeletedAtIsNull`), 기간 한정이 종료됐으면 참여 불가, 중복 참여는 `uk`와 서비스 검사로 이중 방지.

## 🗄️ 마이그레이션

- `V24__challenge_ticket_on_user.sql` — `users`에 개설권 카운트/연월 컬럼 추가 + `challenge_ticket` 테이블 DROP.
- (챌린지 테이블 자체는 기존 `V23`에서 생성됨)

## 📸 테스트 결과

`./gradlew test --tests "com.backend_catcheat.domain.challenge.*"` (DB 없이 Mockito)

- `ChallengeServiceTest` — 개설권 리필(3개), 정상 개설(티켓 소진+슬롯 저장), 슬롯 5개 미만, LIMITED 종료시각 누락, 개설권 소진.
- `ChallengeParticipationServiceTest` — 참여 성공/없음/중복/종료.

## 🔍 리뷰 포인트

**뱃지·랭킹(김락현)과의 접점**
- 개설 시 `reward_badge_id`는 지금 요청값을 그대로 저장(선택)합니다. 뱃지 커스텀이 붙으면 "개설 시 뱃지 생성 → id 연결"로 이어붙일 예정입니다.
- 완료 시 뱃지 지급, 랭킹(참여자수·해금수·조회수)은 EPIC C/랭킹 담당과 데이터 소스를 공유합니다.

**개설권 리필이 조회에서도 발생**
`getRemainingTickets`가 `@Transactional`(readOnly 아님)인 이유는, 조회 시 월이 바뀌면 리필로 User를 수정하기 때문입니다.

## 참고사항 (후속 이슈)

- **슬롯 해금/인증 + 완료 판정** — `ChallengeUnlock` 엔티티/Repository는 미리 만들어 뒀고, unlock 서비스·API는 다음 PR.
- **챌린지 탐색(진행중/완료 목록)**, **이벤트 도감(운영진 개설)** 도 후속.
- FE는 별도 진행.
