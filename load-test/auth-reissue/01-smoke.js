/**
 * 01 · 스모크 — 스크립트·인증·회전이 맞는지 확인하는 30초짜리. 서버 성능 측정 아님.
 * checks가 모두 100%가 아니면 여기서 멈추고 02로 가지 말 것.
 *
 * 재발급은 상태 회전이라 VU당 1회 로그인 후 같은 세션의 refresh를 반복 회전시킨다.
 */
import http from 'k6/http'
import { check, sleep } from 'k6'
import { NAME, url, contentOk, loginThisVU, carryCookies } from './target.js'

export const options = {
    vus: 1,
    duration: '30s',
    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
    },
}

// k6는 module 스코프가 VU별로 독립 → VU당 1회만 로그인하는 플래그
let ready = false

export default function () {
    if (!ready) {
        loginThisVU()
        ready = true
        console.log(`[${NAME}] VU 로그인 완료 — 회전 시작`)
    }

    const res = http.post(url(), null)
    carryCookies(res) // 회전된 새 쿠키를 다음 요청으로 이어받는다

    check(res, {
        '200': (r) => r.status === 200,
        'success=true': (r) => {
            try {
                return r.json('success') === true
            } catch {
                return false
            }
        },
        '회전됨(새 access 쿠키)': (r) => contentOk(r),
    })

    sleep(1)
}

export function teardown() {
    console.log('스모크 끝 — checks 100%면 02-baseline으로')
}
