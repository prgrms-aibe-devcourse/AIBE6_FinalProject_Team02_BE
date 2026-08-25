/**
 * 측정 대상 — access 재발급(refresh 회전, RTR).
 *
 *   POST /api/v1/auth/reissue   (쿠키의 refresh_token으로 회전 → 새 access/refresh를 Set-Cookie)
 *
 * ⚠️ 목록 조회(GET)와 성격이 다르다 — 이 API는 **상태를 바꾼다(refresh 회전)**.
 *   - 여러 VU가 같은 refresh를 공유해 동시에 때리면 CAS 때문에 대부분 grace/탈취(401)가 나
 *     처리량이 안 나온다. 그래서 **VU마다 자기 세션을 하나 갖고 그 refresh를 반복 회전**시킨다
 *     (= 사용자 한 명이 시간에 걸쳐 계속 재발급하는 실제 상황).
 *   - k6는 VU마다 쿠키 자(jar)가 독립이라, VU당 1회 로그인 후 reissue를 돌리면
 *     응답의 Set-Cookie(새 refresh)가 그 VU 자에 자동 반영돼 회전이 이어진다.
 *   - 세션은 VU 수만큼만 쌓인다(리뷰어 계정 1명에 sid 여러 개). 테스트 후 Redis에 남는다.
 */
import http from 'k6/http'
import { fail } from 'k6'
import { BASE_URL, DEFAULT_THRESHOLDS, TEST_LOGIN_KEY } from '../lib/config.js'

/** 화면 이름 — 로그·결과 파일에 찍힌다 */
export const NAME = 'access 재발급(회전)'

/**
 * 이 API의 성능을 지배하는 변수는 **동시 재발급 수(VU=독립 세션 수)**다.
 * 호출당 비용: Redis Lua CAS(회전) + JWT 서명 2회(access·refresh) + 유저 1건 조회(role).
 * 상태 회전이라 VU가 refresh를 공유하면 안 된다 — VU마다 세션 하나.
 */
export const DATA_NOTE = '동시 재발급 수(VU=독립 세션). 호출당 Redis CAS + JWT 서명 2회 + 유저 조회 1회'

/**
 * 재발급 응답의 새 쿠키(access·refresh)를 이 VU의 쿠키 자(jar)에 명시적으로 심는다.
 * k6 자동 저장에만 맡기면 회전 체인이 끊겨(첫 건만 성공) 이후 전부 401이 난다.
 * 다음 요청이 반드시 방금 회전된 refresh를 들고 가도록 직접 set한다.
 */
export function carryCookies(res) {
    const jar = http.cookieJar()
    const rt = res.cookies && res.cookies['refresh_token']
    if (rt && rt[0]) jar.set(BASE_URL, 'refresh_token', rt[0].value)
    const at = res.cookies && res.cookies['access_token']
    if (at && at[0]) jar.set(BASE_URL, 'access_token', at[0].value)
}

/** 재발급 URL. CSRF 예외 목록(`/api/v1/auth/reissue`)이라 X-XSRF-TOKEN 불필요 */
export function url() {
    return `${BASE_URL}/api/v1/auth/reissue`
}

/** test-login URL — VU가 자기 세션(access+refresh 쿠키)을 만들 때 쓴다 */
export const TEST_LOGIN_URL = `${BASE_URL}/api/v1/auth/test-login${TEST_LOGIN_KEY ? `?key=${TEST_LOGIN_KEY}` : ''}`

/**
 * 이 VU를 로그인시켜 쿠키 자(jar)에 access+refresh를 심는다.
 * 공유 setup() 토큰을 안 쓰는 이유: 회전은 세션별이라 VU가 각자 refresh를 가져야 한다.
 * 쿠키는 k6가 이 VU의 자에 자동 저장한다(HttpOnly 무시).
 */
export function loginThisVU() {
    const res = http.post(TEST_LOGIN_URL, null)
    if (res.status === 404) fail(`test-login 404 — 서버에 TEST_LOGIN_ENABLED=true 필요 (${TEST_LOGIN_URL})`)
    if (res.status !== 200) fail(`test-login 실패 status=${res.status}`)
}

/**
 * 재발급 응답이 맞는지. reissue는 `ApiResponse<Void>`라 data가 null이므로 `success`로 판정한다.
 * 그리고 200이어도 회전이 안 됐을 수 있어, **새 access 쿠키가 내려왔는지**까지 본다.
 */
export function contentOk(res) {
    try {
        const ok = res.json('success') === true
        const rotated = res.cookies && res.cookies['access_token'] && res.cookies['access_token'][0]
        return ok && !!rotated
    } catch {
        return false
    }
}

/**
 * 임계값 — **02-baseline 실측 후** 채운다. 처음엔 DEFAULT를 두고 돌려 그 자체를 정보로 삼는다.
 * 재발급은 Redis 왕복+서명이라 목록 조회보다 약간 무거울 수 있으니 실측 × 2~3배로 조인다.
 */
export const THRESHOLDS = {
    ...DEFAULT_THRESHOLDS,
    // http_req_duration: ['p(95)<XX', 'p(99)<YY'],   // ← baseline 실측 후 주석 해제·교체
}
