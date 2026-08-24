/**
 * 03 · 부하 곡선 — VU를 올려 가며 때린다
 *
 *   .\load-test\run.ps1 -Domain logit-feed -Stage load -Vus 150 -Tag before
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
 * ## 이 도메인에서 실제로 나온 그림 (2026-08-24, VU 150, M=12)
 *
 * 처음에는 "서명은 순수 CPU라 커넥션 대기는 0에 가까울 것"으로 예상했다. **틀렸다.**
 * 실측은 커넥션 사용 10/10 · **대기 112** · 획득 대기 6.03초 · CPU는 35%였다.
 *
 * 이유는 findFeed가 @Transactional(readOnly = true) 안에서 돌기 때문이다.
 * 서명 60회(약 120ms)는 DB를 전혀 쓰지 않는데도 **그동안 커넥션을 붙잡고 있다.**
 * 그래서 CPU 병목이 아니라 커넥션풀 고갈로 나타났다.
 *
 * 교훈: 밖(k6)에서 본 "느리다"와 안(Prometheus)에서 본 "왜"가 다를 수 있다.
 * 예상과 다르면 예상이 아니라 데이터를 따른다.
 *
 * ## ⚠️ RPS를 개선 지표로 쓰지 말 것
 *
 * 아래 think time 때문에 **부하 생성기 쪽에 상한**이 있다. VU 150이면 이론상 최대 RPS는
 * 150 ÷ 평균 2초 ≈ 75다. 서버가 10배 빨라져도 이 숫자는 안 오른다.
 * 서버 한계를 알려면 VU를 더 올리거나 ramping-arrival-rate(개방형)로 RPS를 직접 몰아야 한다.
 *
 * ⚠️ 배포 서버로 쏘지 말 것. dev와 prod가 같은 EC2 한 대(t3 계열)를 쓴다 —
 *    dev에 부하를 거는 건 운영에 거는 것과 물리적으로 같은 일이고,
 *    CPU 크레딧이 소진되면 측정 중에 서버 성능이 바뀌어 before/after 비교가 깨진다.
 */
import http from 'k6/http'
import { check, sleep } from 'k6'
import { rampingStages } from '../lib/config.js'
import { login, useToken } from '../lib/auth.js'
import { NAME, THRESHOLDS, contentOk, discoverMadeDexId, url } from './target.js'

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
    const accessToken = login()
    useToken(accessToken)
    const madeDexId = discoverMadeDexId()
    console.log(`[${NAME}] 최대 VU=${PEAK} · madeDexId=${madeDexId}`)
    return { accessToken, madeDexId }
}

export default function (data) {
    useToken(data.accessToken)

    const res = http.get(url({ madeDexId: data.madeDexId }))

    check(res, {
        '200': (r) => r.status === 200,
        '썸네일이 실린 카드가 있음': (r) => contentOk(r),
    })

    // think time — 실제 사용자는 화면을 보고 잠깐 멈춘다.
    // 이게 없으면 VU 1명이 사람 20명분 부하를 내서 "VU 50 = 사용자 50명"이 성립하지 않는다
    sleep(Math.random() * 2 + 1) // 1~3초
}
