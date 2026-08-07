## 📌 관련 이슈

Jira: CATCHEAT-50 (EPIC B — 챌린지 도감 개편)

## ✨ 작업 내용

챌린지를 **위치 인증 · 수집형 전용**으로 단순화하고, 목표 음식에 **가게명·설명**을 추가했습니다.

- **인증 방식·유형 제거** — 챌린지는 항상 위치 인증(LOCATION), 항상 수집형(COLLECTION)이라 `VerifyType`·`ChallengeType` enum을 걷어냄
- **목표 음식 확장** — 슬롯에 `store_name`(가게명), `description`(설명) 추가

## 커밋

| 커밋 | 내용 |
|---|---|
| `f663d61` feat | 사진 인증 제거, 선착순 제거 (verifyType·challengeType enum 삭제) |
| `6390b54` feat | 가게명 컬럼 추가 (+ 설명) |

## 주요 변경

**1. `VerifyType`·`ChallengeType` 제거**
값이 하나뿐인 enum이라 코드·스키마에서 모두 제거했습니다.
- enum 파일 삭제, `ChallengeDex` 필드·빌더 제거
- `ChallengeCreateRequestDTO`·`ChallengeDetailResponseDTO`·`ChallengeSummaryDTO`에서 두 필드 제거
- `ChallengeService`: create·validate·toSummary·getDetail 정리 (좌표는 항상 필수)
- `ChallengeParticipationService.unlock`: 조건 분기 없이 **항상 위치 검증**
- 컬럼 DROP: `V35`

**2. 목표 음식 가게명·설명**
- 슬롯에 `store_name`·`description` 컬럼 추가 → `V36`
- `ChallengeDexSlot` 엔티티, `SlotInput`(개설 요청), `SlotDetail`(상세 응답)에 반영
- `ChallengeService` create 저장 / getDetail 응답 포함

## 🗄️ 마이그레이션

- `V35__..._challenge_description.sql` — `challenge_dex`에서 `challenge_type`·`verify_type` 컬럼 DROP
- `V36__..._challenge_slot_store_description.sql` — `challenge_dex_slot`에 `store_name`·`description` 추가

> 컬럼 DROP이 있어 dev는 `docker compose down -v && up -d`로 재생성 필요(기존 dev 데이터 초기화).

## ⚠️ 참고

- 챌린지 단위 테스트(`ChallengeServiceTest`·`ChallengeParticipationServiceTest`)는 변경된 스펙(타입/인증 enum 제거, 슬롯 필드 추가)에 맞게 수정했습니다.
