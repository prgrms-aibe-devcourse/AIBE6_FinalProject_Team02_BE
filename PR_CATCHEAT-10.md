## 📌 관련 이슈

Jira: CATCHEAT-10

## ✨ 작업 내용

관리자 콘솔(`admin`) 도메인을 신설했습니다. 관리자가 처리해야 할 두 개의 큐를 목록 조회 + 채택·완료·반려로 구현했습니다.

- **미확인 음식 제보 큐** — 도감에 없는 음식 제보 → 채택/반려
- **음식 등록 요청 큐** — AI가 끝까지 판정하지 못한 등록 → 완료(칸 해금)/반려

## 📢 CATCHEAT-6의 리뷰 큐를 등록 요청 큐로 통합했습니다 — 병합 전 확인 필요

회의에서 "AI 실패 시 관리자에게 등록 요청" 흐름을 admin이 맡기로 하면서, CATCHEAT-6이 먼저 구현한 `ReviewQueueItem` 검토 큐와 기능이 겹쳤습니다. 두 개를 유지하면 테이블·API가 이중이 되어, **리뷰 큐를 admin의 `FoodRegistrationRequest`로 통합**했습니다.

- 삭제: `ReviewQueueItem`·`ReviewStatus`·`ReviewQueueItemRepository`·`ReviewService`·`AdminReviewController`·`ReviewItemResponse` (6파일)
- 생산자 `RegistrationConfirmService`가 `FoodRegistrationRequest`를 생성하도록 rewire
- `V15` 마이그레이션에서 `review_queue_item` 드롭

CATCHEAT-6 담당자 확인 후 병합 부탁드립니다.

## 커밋

| 커밋 | 내용 |
|---|---|
| `491aacb` feat | 제보 플로우 (entity, dto, repo, service, controller) |
| `0171a33`·`6d1626e` feat | 음식 등록 요청 플로우 (entity, service, controller, dto) |
| `d89d4c7` feat | admin DB 마이그레이션 (V8) |
| `3ffaf86` test | AdminService 단위 테스트 |
| `bb3ee7f` fix | develop 병합 + 리뷰 큐 통합(교체·dex 연동·V15) |

## API 엔드포인트 (ADMIN 전용, `/api/v1/admin`)

| 메서드 · 경로 | 동작 |
|---|---|
| `GET /reports` | 대기 제보 목록 |
| `PATCH /reports/{id}/accept` | 제보 채택 |
| `PATCH /reports/{id}/reject` `{reason}` | 제보 반려 |
| `GET /registration-requests` | 대기 등록 요청 목록 (증빙 URL 포함) |
| `PATCH /registration-requests/{id}/complete` | 등록 완료 → **칸 해금** |
| `PATCH /registration-requests/{id}/reject` `{reason}` | 등록 반려 |

## 주요 구현

**1. "완료"가 곧 해금 시점입니다**
`completeRequest`가 검토 대기 카드를 유저 컬렉션에 붙여 칸을 열고, 별 랭크와 수집률을 반영합니다. 랭크를 등록 시점이 아니라 여기서 매기는 이유: 대기 중 다른 등록으로 같은 칸이 먼저 열렸을 수 있기 때문입니다.

**2. AI 실패 사유와 반려 사유를 분리했습니다**
`failureReason`(AI가 왜 못 끝냈는지)과 `rejectReason`(관리자가 왜 반려했는지)은 다른 축이라 서로 덮어쓰지 않습니다. 반려는 사유를 필수로 받습니다.

**3. 인가는 컨트롤러가 아니라 URL 단위입니다**
경로가 `/api/v1/admin/**`이라 `SecurityConfig`의 `hasRole("ADMIN")`이 막아, 컨트롤러엔 권한 코드가 없습니다. ADMIN이 아니면 도달 전에 403입니다.

**4. 증빙 사진은 presigned URL로만 내려줍니다**
버킷이 비공개라 목록 응답의 `evidenceUrl`은 만료되는 주소입니다.

## 📸 테스트 결과

`AdminServiceTest` 7케이스로 로직을 검증했습니다 (DB 없이 Mockito).

```
제보:    채택→ACCEPTED / 반려→REJECTED+사유 / 없음→404 / 이미처리→409
등록요청: 완료→카드 해금+COMPLETED / 반려→rejectReason만 채움(failureReason 보존) / 없음→404
```

`./gradlew build` — **61 tests pass / 0 fail** (생산자 rewire 반영해 `RegistrationConfirmServiceTest`도 갱신).

⚠️ 브라우저 실제 왕복(로그인 → ADMIN 승격 → 목록 조회 → 완료로 칸 열림)은 아직 검증하지 못했습니다. BE+DB를 띄워 한 번 돌려봐야 합니다.

## 🔍 리뷰 포인트

**⚠️ 팀원 코드 삭제 — CATCHEAT-6 확인 요망**
리뷰 큐 6파일 삭제 + `RegistrationConfirmService` 수정이 포함됩니다. 위 통합 근거를 함께 봐주세요.

**⚠️ Flyway 마이그레이션 순서**
우리 `V8`은 번호가 develop의 `V10~V14`보다 낮습니다. develop만 적용된 DB에 이 브랜치를 얹으면 out-of-order로 기동이 막힐 수 있습니다. `spring.flyway.out-of-order: true`로 열거나 `V8`·`V15`를 재번호할지 논의가 필요합니다.

**관리자 계정 지정은 수동입니다**
관리자 생성 UI가 없어(소셜 로그인만), 로그인 1회 후 `UPDATE users SET role='ADMIN' WHERE email=...`로 승격한 뒤 재로그인해야 합니다.

## 참고사항

- **제보 큐**(`UnidentifiedFoodReport`)는 "새 마스터 도감 칸 추가 요청"으로, 리뷰 큐(개인 카드 승인)와 성격이 다릅니다. 채택 시 신규 슬롯 생성 로직은 dex 도메인 준비 후 연결 예정이며, 현재는 상태만 변경합니다.
- `ErrorCode`의 `REVIEW_*` → `REPORT_NOT_FOUND`·`REGISTRATION_REQUEST_NOT_FOUND`·`ADMIN_ITEM_ALREADY_HANDLED`로 교체했습니다.
- FE 관리자 콘솔은 별도로 진행 중입니다.
