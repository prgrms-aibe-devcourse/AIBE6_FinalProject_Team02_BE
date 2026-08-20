/**
 * 03 · 부하 곡선 — VU를 올려 가며 때린다
 *
 *   powershell -File load-test/run.ps1 -Domain <내-기능> -Stage load -Vus 150 -Tag before
 *
 * ## 무엇을 보나
 *
 * VU를 올릴 때 **RPS와 응답시간이 갈라지는 지점(무릎점)**을 찾는다.
 * RPS는 안 오르는데 응답시간만 치솟으면 거기가 서버의 실제 용량이다.
 *
 * 갈라진 뒤 Grafana(localhost:3001)에서 원인을 가른다:
 *   커넥션 대기 > 0    → 커넥션풀 병목
 *   CPU 높음           → CPU 병목
 *   둘 다 낮은데 느림   → 락·외부 IO 대기
 *
 * ## ⚠️ RPS를 개선 지표로 쓰지 말 것
 *
 * 아래 think time 때문에 **부하 생성기 쪽에 상한**이 있다. VU 150이면 이론상 최대 RPS는
 * 150 ÷ 평균 2초 ≈ 75다. 서버가 10배 빨라져도 이 숫자는 안 오른다.
 * 서버 한계를 알려면 VU를 더 올리거나 `ramping-arrival-rate`(개방형)로 RPS를 직접 몰아야 한다.
 *
 * ⚠️ 배포 서버로 쏘기 전 팀에 공지할 것.
 */
import http from 'k6/http'
import { check, sleep } from 'k6'
import { rampingStages } from '../lib/config.js'
import { login, useToken } from '../lib/auth.js'
import { NAME, THRESHOLDS, contentOk, url } from './target.js'

const PEAK = Number(__ENV.VUS || 50)

export const options = {
    scenarios: {
        main: {
            // 폐쇄형(ramping-vus) — 동시 사용자 수를 고정하고 올린다.
            // 서버가 느려지면 부하도 같이 줄어 서버를 급사시키지 않는다
            executor: 'ramping-vus',
            startVUs: 0,
            stages: rampingStages(PEAK),
            gracefulRampDown: '20s',
        },
    },
    thresholds: THRESHOLDS,
}

export function setup() {
    console.log(`[${NAME}] 최대 VU=${PEAK}`)
    return { accessToken: login() }
}

export default function (data) {
    useToken(data.accessToken)

    const res = http.get(url())

    check(res, {
        '200': (r) => r.status === 200,
        '응답 모양 맞음': (r) => contentOk(r),
    })

    // think time — 실제 사용자는 화면을 보고 잠깐 멈춘다.
    // 이게 없으면 VU 1명이 사람 20명분 부하를 내서 "VU 50 = 사용자 50명"이 성립하지 않는다
    sleep(Math.random() * 2 + 1) // 1~3초
}
