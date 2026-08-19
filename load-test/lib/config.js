/**
 * 부하테스트 **공통** 설정 — 도메인(어떤 API를 재는지)과 무관한 것만 둔다.
 *
 * 어떤 URL을 때릴지, 임계값을 얼마로 할지는 **각 도메인 폴더의 `target.js`**가 정한다.
 * 예전에는 여기에 챌린지 랭킹의 `SORTS`·`challengesUrl()`이 섞여 있었다. 그러면 다른 기능을
 * 재려는 팀원이 자기 것과 무관한 챌린지 코드를 읽고 고쳐야 한다.
 *
 *   load-test/
 *   ├ lib/                 ← 여기 (공통: 서버 주소·인증·기본 임계값·부하 단계)
 *   ├ challenge-ranking/   ← 도메인 (target.js + 01·02·03)
 *   └ _template/           ← 새 도메인 시작점
 *
 * 값은 전부 환경변수로 덮어쓸 수 있다 — 스크립트를 고치지 않고 대상만 바꾸려는 것.
 */

/** 때릴 서버. 배포로 쏠 때는 반드시 팀에 먼저 공유할 것 */
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'

/** test-login에 시크릿이 걸려 있으면 넣는다. 비어 있으면 검사 안 함 */
export const TEST_LOGIN_KEY = __ENV.TEST_LOGIN_KEY || ''

/** 서버가 심는 인증 쿠키 이름 (OAuth2SuccessHandler.ACCESS_TOKEN_COOKIE와 같아야 함) */
export const ACCESS_COOKIE = 'access_token'

/**
 * 임계값의 **출발점**. 그대로 쓰라는 뜻이 아니다.
 *
 * 이 값은 "일반적인 목록 조회 API"의 관례일 뿐 우리 서버를 보고 정한 게 아니다.
 * 02-baseline으로 실측한 뒤 **각 도메인의 `target.js`에서 실측 × 2~3배로 다시 정한다.**
 * 첫 실행에서 통과하든 실패하든 그 자체가 정보다 — 통과하면 조이고, 실패하면 원인을 본다.
 */
export const DEFAULT_THRESHOLDS = {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
}

/**
 * 부하 곡선 단계. `ramping-vus`(폐쇄형 — 동시 사용자 수를 고정하고 올린다)용.
 *
 * 예열 → 중간 → 목표 → **측정 구간** → 회복 순서다. 결과는 측정 구간만 본다.
 * 예열을 두는 이유: JIT·커넥션풀이 데워지기 전 숫자가 섞이면 서버가 실제보다 느려 보인다.
 *
 *   "초당 N건이 들어오면?"을 재려면 ramping-arrival-rate(개방형)로 바꾼다.
 *   폐쇄형은 서버가 느려지면 부하도 같이 줄어서 한계를 넘겨 보지 못한다
 */
export function rampingStages(peak) {
    return [
        { duration: '30s', target: Math.round(peak * 0.2) },
        { duration: '30s', target: Math.round(peak * 0.5) },
        { duration: '30s', target: peak },
        { duration: '3m', target: peak }, // ★ 측정 구간
        { duration: '30s', target: 0 },
    ]
}
