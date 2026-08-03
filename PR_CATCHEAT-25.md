## 📌 관련 이슈

Jira: CATCHEAT-25 (EPIC B — 챌린지 도감)

## ✨ 작업 내용

챌린지 도감의 **슬롯 해금(인증) · 탐색 · 상세보기**까지 백엔드를 완성하고, 개설 시 등록한 **목표 음식 사진**을 상세 도감에서 흑백/컬러로 보여주기 위한 스키마·응답을 추가했습니다. 앞선 개설·참여(CATCHEAT-20) 위에 이어지는 작업입니다.

- **슬롯 해금(인증)** — 참가자가 목표 음식 사진을 올려 슬롯을 하나씩 해금, 전부 채우면 자동 완주 처리
- **탐색** — 진행중/완료 챌린지 목록(참여자 수 포함)
- **상세보기** — 챌린지 + 슬롯 목록 + 내 참여/완료/해금 상태
- **목표 음식 사진** — 개설자가 슬롯마다 등록한 사진을 상세 응답에 프리사인 URL로 내려줌 (미해금이면 프론트에서 흑백 표시)

## 커밋

| 커밋 | 내용 |
|---|---|
| `a4fd649` feat | 해금 플로우 추가 |
| `b68db76` feat | 해금 테스트 코드 추가 |
| `dbf9e57` feat | 탐색 플로우 추가 |
| `37e69b1` feat | 탐색 테스트 코드 추가 |
| `2a783c7` feat | 상세보기 api 추가 |
| `9572ef1` feat | 목표 음식 사진(image_key) 추가 및 기능 수정 |

## API 엔드포인트 (`/api/v1/challenges`, 인증 사용자)

| 메서드 · 경로 | 동작 |
|---|---|
| `POST /{id}/unlocks` | 슬롯 해금(인증) — `{ slotId, imageKey }` |
| `GET /?status=ONGOING\|FINISHED` | 탐색 목록 (진행중/완료) |
| `GET /{challengeId}` | 챌린지 상세 (슬롯 · 내 진행 상태 포함) |

## 주요 구현

**1. 해금 = 참여 확인 → 슬롯 소유 확인 → 중복 방지 → 저장 → 완주 판정**
내가 참여한 챌린지인지(`CHALLENGE_NOT_JOINED`), 그 슬롯이 이 챌린지의 슬롯인지(`CHALLENGE_SLOT_NOT_FOUND`), 이미 해금한 슬롯은 아닌지(`CHALLENGE_SLOT_ALREADY_UNLOCKED`)를 차례로 검증합니다. 저장 후 해금 수가 전체 슬롯 수 이상이면 참가자를 완주 처리(`participant.complete()`)하고, 응답으로 `{ unlockedCount, totalSlots, completed }`를 돌려줍니다.

**2. 탐색은 진행중/완료를 쿼리로 분리**
`ChallengeListStatus`(ONGOING/FINISHED)에 따라 `findOngoing`/`findFinished`로 조회하고, 각 챌린지의 참여자 수를 함께 담아 요약 DTO로 내려줍니다. 소프트 삭제된 챌린지는 제외됩니다.

**3. 상세는 "내 시점"을 함께 계산**
슬롯 목록에 더해, 요청자가 참여했는지·완주했는지, 슬롯별로 내가 해금했는지를 계산해 한 번에 내려줍니다. 참여 이력이 없으면 모두 미해금으로 표시됩니다.

**4. 목표 음식 사진 — 슬롯에 `image_key` 추가**
개설자가 슬롯마다 등록한 사진을 S3 object key로 `challenge_dex_slot.image_key`에 저장합니다(선택 컬럼). 상세 응답에서는 key를 `S3PresignedUrlService.createDownloadUrl`로 조회용 프리사인 URL(`imageUrl`)로 변환해 내려주고, key가 없으면 null입니다. **미해금이면 프론트에서 흑백, 인증하면 컬러**로 보이도록 하기 위한 데이터입니다.

## 🗄️ 마이그레이션

- `V27__260803_1123_challenge_slot_image.sql` — `challenge_dex_slot`에 `image_key VARCHAR(512)` 추가 (nullable, `ALTER ADD COLUMN`이라 기존 데이터/DB 재생성 불필요).

## 📸 테스트 결과

`./gradlew test --tests "com.backend_catcheat.domain.challenge.*"` (DB 없이 Mockito)

- `ChallengeParticipationServiceTest` — 해금 성공, 미참여, 슬롯 없음, 중복 해금, 마지막 슬롯 해금 시 완주 처리.
- `ChallengeServiceTest` — 탐색(진행중/완료) 목록, 상세 매핑, 개설권/개설(기존).

## 🔍 리뷰 포인트

- `image_key`는 선택 컬럼이라 기존에 만든 슬롯은 상세에서 `imageUrl`이 null로 내려갑니다(프론트는 기본 이미지로 대체).
- 완주 판정을 해금 저장과 같은 트랜잭션에서 처리해, 마지막 해금과 동시에 완료 상태가 반영됩니다.
