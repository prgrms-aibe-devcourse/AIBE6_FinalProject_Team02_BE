/**
 * 03 · 부하 곡선 — VU를 올려 가며 때린다
 *
 *   powershell -File load-test/run.ps1 -Stage load -Vus 150 -Tag before
 *   powershell -File load-test/run.ps1 -Stage load -Vus 150 -Sort LATEST   (비교군)
 *
 * ## 무엇을 보나
 *
 * VU를 올릴 때 **RPS와 응답시간이 갈라지는 지점(무릎점)**을 찾는다.
 * RPS는 안 오르는데 응답시간만 치솟기 시작하면 거기가 우리 서버의 실제 용량이다.
 *
 * 갈라진 뒤 Grafana에서 원인을 가른다:
 *   커넥션 대기 > 0     → 커넥션풀 병목 (풀 크기 or 느린 쿼리)
 *   CPU 높음            → CPU 병목
 *   둘 다 낮은데 느림    → 락·외부 IO 대기
 *
 * ## ⚠️ RPS를 개선 지표로 쓰지 말 것
 *
 * 아래 think time 때문에 **부하 생성기 쪽에 상한**이 있다. VU 150이면 이론상 최대 RPS는
 * 150 ÷ 평균 2초 ≈ 75다. 서버가 10배 빨라져도 이 숫자는 안 오른다.
 * 2026-08-19 측정에서 전 54.3 → 후 57.4로 거의 그대로였는데, 같은 부하를 응답시간 1/14,
 * CPU 1/23로 처리한 것이었다. **서버 한계를 알려면** VU를 더 올리거나
 * `ramping-arrival-rate`(개방형)로 RPS를 직접 몰아야 한다.
 *
 * ⚠️ 배포 서버(BASE_URL=https://api.projectjm.co.kr)에 쏘기 전 확인:
 *    팀 공지 · 시연 일정 · EC2 인스턴스 타입(t계열이면 CPU 크레딧 소진 주의) · 낮은 VU부터
 */
import http from 'k6/http'
import { check, sleep } from 'k6'
import { rampingStages } from '../lib/config.js'
import { login, useToken } from '../lib/auth.js'
import { DEFAULT_SORT, NAME, THRESHOLDS, contentOk, url } from './target.js'

const SORT = __ENV.SORT || DEFAULT_SORT
const PEAK = Number(__ENV.VUS || 50)

export const options = {
    scenarios: {
        ranking: {
            // ramping-vus = 폐쇄형 모델. 동시 사용자 수를 고정하고 올린다.
            // 초보자에게 직관적이고 서버를 급사시키지 않는다
            executor: 'ramping-vus',
            startVUs: 0,
            stages: rampingStages(PEAK),
            gracefulRampDown: '20s',
            tags: { sort: SORT },
        },
    },
    thresholds: THRESHOLDS,
}

export function setup() {
    console.log(`[${NAME}] sort=${SORT} · 최대 VU=${PEAK}`)
    return { accessToken: login() }
}

export default function (data) {
    useToken(data.accessToken)

    const res = http.get(url({ sort: SORT }), { tags: { sort: SORT } })

    check(res, {
        '200': (r) => r.status === 200,
        // 200인데 내용이 빈 경우를 잡는다 — 상태코드만 보면 못 잡는 장애
        'content 있음': (r) => contentOk(r),
    })

    // think time — 실제 사용자는 목록을 보고 잠깐 멈춘다.
    // 이게 없으면 VU 1명이 사람 20명분 부하를 내서 "VU 50 = 사용자 50명"이 성립하지 않는다
    sleep(Math.random() * 2 + 1) // 1~3초
}
