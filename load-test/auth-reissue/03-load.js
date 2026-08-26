/**
 * 03 · 부하 곡선 — VU를 올려 가며 동시 재발급을 때린다.
 *
 *   run.ps1 -Domain auth-reissue -Stage load -Vus 150 -Tag before
 *
 * VU마다 자기 세션을 하나 갖고 그 refresh를 회전시킨다(공유 금지).
 * RPS와 응답시간이 갈라지는 무릎점을 찾고, 느려지면 Grafana에서 원인(주로 Redis 왕복·DB 조회·CPU)을 가른다.
 * ⚠️ RPS는 think time 때문에 상한이 있으니 개선 지표로 쓰지 말 것(README §3).
 */
import http from 'k6/http'
import { check, sleep } from 'k6'
import { rampingStages } from '../lib/config.js'
import { NAME, THRESHOLDS, url, contentOk, loginThisVU, carryCookies } from './target.js'

const PEAK = Number(__ENV.VUS || 50)

export const options = {
    scenarios: {
        main: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: rampingStages(PEAK),
            gracefulRampDown: '20s',
        },
    },
    thresholds: THRESHOLDS,
}

let ready = false

export default function () {
    if (!ready) {
        loginThisVU() // 이 VU의 세션 1개 생성(이후 이 세션의 refresh를 반복 회전)
        ready = true
    }

    const res = http.post(url(), null)
    carryCookies(res) // 회전된 새 쿠키를 다음 요청으로 이어받는다

    check(res, {
        '200': (r) => r.status === 200,
        '회전됨': (r) => contentOk(r),
    })

    // think time — 실제 사용자는 access가 만료될 때쯤에야 재발급한다. 1~3초로 둔다
    sleep(Math.random() * 2 + 1)
}
