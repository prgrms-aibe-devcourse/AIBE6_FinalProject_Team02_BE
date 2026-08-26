/**
 * 측정 대상 — 내 챌린지 목록.
 *
 *   GET /api/v1/challenges/mine?relation=JOINED   (참여 중인 챌린지 + 내 진행도)
 *
 * ⚠️ 이 API의 성능을 지배하는 변수는 **VU가 아니라 참여 챌린지 개수 N**이다.
 *   ChallengeService.getMyChallenges가 목록을 돌며 챌린지마다 쿼리를 3번씩 더 쏜다
 *   (슬롯 수 count + 내 참여 조회 + 해금 수 count) → 전형적 N+1. N이 늘수록 선형으로 느려진다.
 *   데이터가 몇 건뿐인 DB에 VU만 올리면 "빠르다"만 나온다 → seed/로 N을 넣고 재야 곡선이 보인다.
 */
import { BASE_URL, DEFAULT_THRESHOLDS } from '../lib/config.js'

/** 화면 이름 — 로그·결과 파일에 찍힌다 */
export const NAME = '내 챌린지 목록(JOINED)'

/** 성능을 지배하는 변수 = 참여(JOINED) 챌린지 개수 N (seed/seed-mine.sql) */
export const DATA_NOTE = '참여 챌린지 개수 N. 건별 슬롯/참여/해금 조회 N+1 → N에 선형 비례'

/** 목록 URL. relation은 CREATED / JOINED / COMPLETED 중 하나(서버 MyChallengeRelation) */
export function url({ relation = 'JOINED' } = {}) {
    return `${BASE_URL}/api/v1/challenges/mine?relation=${relation}`
}

/**
 * 응답이 맞는지. 200만 보면 "200인데 빈 배열" 장애를 놓친다.
 * 서버 응답 껍데기는 `ApiResponse<List<ChallengeSummary>>` — data가 배열이다.
 */
export function contentOk(res) {
    try {
        return Array.isArray(res.json('data'))
    } catch {
        return false
    }
}

/**
 * 임계값 — 02-baseline을 **N을 바꿔가며(예: 5/50/500)** 실측한 뒤 채운다.
 * N+1이면 N=500에서 크게 느려질 것이다. 개선(배치) 후 그 기울기가 평평해지는지 본다.
 */
export const THRESHOLDS = {
    ...DEFAULT_THRESHOLDS,
    // http_req_duration: ['p(95)<XX', 'p(99)<YY'],   // ← baseline 실측 후 교체
}
