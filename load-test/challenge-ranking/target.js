/**
 * 측정 대상 — 챌린짓 탐색 목록(랭킹 정렬).
 *
 *   GET /api/v1/challenges?status=ONGOING&sort=VIEWS&page=0&size=10
 *
 * **도메인 고유한 것은 전부 이 파일에 모은다.** 01·02·03 스크립트는 이 파일만 보고
 * 실행 방식(스모크/기준선/부하)만 다르게 한다. 다른 API를 재려면 이 파일만 새로 쓰면 된다.
 */
import { BASE_URL, DEFAULT_THRESHOLDS } from '../lib/config.js'

/** 화면 이름 — 로그·결과 파일에 찍힌다 */
export const NAME = '챌린짓 랭킹 목록'

/**
 * 이 API의 성능을 지배하는 변수는 **VU가 아니라 진행중 챌린지 개수 N**이다.
 * 데이터가 3건인 DB에 VU 200을 때려도 "빠르다"만 나온다 — seed/를 먼저 넣을 것.
 */
export const DATA_NOTE = '진행중 챌린지 개수 N (seed/seed-challenges.sql)'

/** 랭킹 정렬 종류 — 서버의 ChallengeSortType과 1:1 */
export const SORTS = ['LATEST', 'VIEWS', 'PARTICIPANTS', 'UNLOCKS']

/**
 * 기본 측정 대상 정렬.
 *
 * VIEWS를 고른 이유 — 집계가 있고(랭킹 비용이 보인다) 시드 데이터의 점수 분포도 넓다.
 * UNLOCKS는 시드가 challenge_unlock을 만들지 않아 점수가 전부 0이다(비용은 유효, 순위는 무의미)
 */
export const DEFAULT_SORT = 'VIEWS'

/** 목록 URL 조립 */
export function url({ sort = DEFAULT_SORT, page = 0, size = 10, status = 'ONGOING' } = {}) {
    return `${BASE_URL}/api/v1/challenges?status=${status}&sort=${sort}&page=${page}&size=${size}`
}

/**
 * 응답이 기대한 모양인지. 200만 보면 "200인데 내용이 빈" 장애를 놓친다.
 * 서버 응답 껍데기는 `ApiResponse<PageResponse<...>>`
 */
export function contentOk(res) {
    try {
        return Array.isArray(res.json('data.content'))
    } catch {
        return false
    }
}

/**
 * 이 API의 임계값.
 *
 * 2026-08-19 개선(집계·정렬·페이징을 DB 쿼리로) 후 실측: N=5,000 · VU 150에서 p95 14.29ms.
 * 실측 × 2~3배 관례로 잡되, **개선 전(209ms)으로 되돌아가면 즉시 깨지도록** 여유를 크게 두지 않았다.
 * 회귀 감지가 임계값의 목적이다
 */
export const THRESHOLDS = {
    ...DEFAULT_THRESHOLDS,
    http_req_duration: ['p(95)<50', 'p(99)<150'],
}
