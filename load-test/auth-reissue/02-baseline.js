/**
 * 02 · 기준선 — 부하 없이(VU 1) 재발급 한 번의 순수 처리시간을 잰다. 03 임계값의 출발점.
 * 임계값은 걸지 않는다(여기선 재는 게 목적).
 */
import http from 'k6/http'
import { check } from 'k6'
import { Trend } from 'k6/metrics'
import { NAME, url, loginThisVU, carryCookies } from './target.js'

export const options = {
    vus: 1,
    duration: '1m',
}

const latency = new Trend('reissue_latency', true)

let ready = false

export default function () {
    if (!ready) {
        loginThisVU()
        ready = true
    }

    const res = http.post(url(), null)
    carryCookies(res) // 회전된 새 쿠키를 다음 요청으로 이어받는다
    latency.add(res.timings.duration)
    check(res, { '200': (r) => r.status === 200 })
    // think time 없음 — 표본을 최대한 많이 모은다
}

export function teardown() {
    console.log('')
    console.log('▸ 요약표의 reissue_latency(또는 http_req_duration) p95를 적어 둔다.')
    console.log('▸ 그 값 × 2~3배가 target.js THRESHOLDS 출발점')
}
