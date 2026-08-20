/**
 * 02 · 기준선 — **부하 없이(VU 1)** 우리 서버가 낼 수 있는 최선값을 잰다
 *
 * 부하가 섞이면 "원래 느린 건지, 몰려서 느린 건지" 구분이 안 된다.
 * 순수 처리 시간을 먼저 알아야 03의 임계값을 정할 수 있다. **모든 기준의 출발점.**
 *
 * ## target.js의 DATA_NOTE가 데이터량이면, N을 바꿔 가며 여러 번 돌린다
 *
 * 예: 500 / 5,000 / 20,000 에서 각각 재면 **기울기**가 보인다.
 * 개선의 성공 기준은 절대값("빨라졌다")이 아니라 기울기("N이 늘어도 안 느려진다")다.
 *
 * 임계값은 걸지 않는다 — 여기서는 재는 게 목적이고 판정은 03에서 한다.
 */
import http from 'k6/http'
import { check } from 'k6'
import { Trend } from 'k6/metrics'
import { login, useToken } from '../lib/auth.js'
import { url } from './target.js'

export const options = {
    vus: 1,
    duration: '1m',
}

/**
 * 별도 Trend를 두는 이유 — 비교하고 싶은 축이 있으면 그 축별로 나눠 담는다.
 * (챌린짓 랭킹은 정렬 4종을 나눠 담아 "집계 비용 = VIEWS − LATEST"를 뽑았다)
 * 비교 축이 없으면 이 줄과 아래 `.add(...)`를 지우고 http_req_duration만 봐도 된다
 */
const latency = new Trend('my_endpoint', true)

export function setup() {
    return { accessToken: login() }
}

export default function (data) {
    useToken(data.accessToken)

    const res = http.get(url())
    latency.add(res.timings.duration)
    check(res, { '200': (r) => r.status === 200 })
    // think time 없음 — 표본을 최대한 많이 모은다
}

export function teardown() {
    console.log('')
    console.log('▸ 요약표의 p95를 적어 둔다. 이 값 × 2~3배가 target.js의 THRESHOLDS 출발점')
    console.log('▸ 데이터량이 변수라면 N을 바꿔 다시 돌려 기울기를 볼 것')
}
