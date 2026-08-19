/**
 * 인증 — 캣칫은 **쿠키 방식**이다.
 *
 * 서버(AuthService.testLogin)가 응답 본문이 아니라 `Set-Cookie`로 토큰을 내려 준다.
 * 그래서 흔한 예제처럼 `Authorization: Bearer ...` 헤더를 쓰면 전부 401/302가 난다.
 *
 * ## 왜 setup()에서 한 번만 부르나
 *
 * test-login은 호출할 때마다 Redis에 refresh 세션(sid)을 하나씩 새로 쌓는다.
 * VU마다 로그인시키면 5분 테스트에 세션 수천 개가 남는다.
 * 리뷰어 계정은 find-or-create라 유저 자체는 안 늘지만, 세션은 늘어난다.
 *
 * → setup()에서 1회 로그인해 토큰만 뽑고, 각 VU가 그 값을 자기 쿠키 자에 심는다.
 */
import http from 'k6/http'
import { fail } from 'k6'
import { ACCESS_COOKIE, BASE_URL, TEST_LOGIN_KEY } from './config.js'

/** setup()에서 1회 호출 — access 토큰 문자열을 돌려준다 */
export function login() {
    const url = `${BASE_URL}/api/v1/auth/test-login${TEST_LOGIN_KEY ? `?key=${TEST_LOGIN_KEY}` : ''}`
    const res = http.post(url, null, { redirects: 0 })

    if (res.status === 404) {
        fail(`test-login이 꺼져 있음(404). 서버에 TEST_LOGIN_ENABLED=true 필요 — ${url}`)
    }
    if (res.status !== 200) {
        fail(`test-login 실패 status=${res.status} url=${url}`)
    }

    const cookie = res.cookies[ACCESS_COOKIE]
    if (!cookie || !cookie[0]) {
        fail(`응답에 ${ACCESS_COOKIE} 쿠키가 없음. 받은 쿠키: ${Object.keys(res.cookies).join(', ') || '(없음)'}`)
    }
    return cookie[0].value
}

/**
 * VU마다 default() 안에서 호출 — 받은 토큰을 이 VU의 쿠키 자에 심는다.
 *
 * setup()의 쿠키 자는 VU로 안 넘어온다. 그래서 값만 넘겨받아 여기서 다시 심는 것
 */
export function useToken(accessToken) {
    http.cookieJar().set(BASE_URL, ACCESS_COOKIE, accessToken)
}
