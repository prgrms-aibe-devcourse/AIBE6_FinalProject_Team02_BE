/**
 * 02 · 기준선(Baseline) — **부하 없이** 우리 서버가 낼 수 있는 최선값을 잰다
 *
 *   powershell -File load-test/run.ps1 -Stage baseline
 *
 * ## 왜 VU 1인가
 *
 * 부하가 섞이면 "원래 느린 건지, 몰려서 느린 건지" 구분이 안 된다.
 * 순수 처리 시간을 먼저 알아야 임계값을 정할 수 있다. **모든 기준의 출발점.**
 *
 * ## 이 스크립트의 진짜 목적 — 랭킹 집계 비용을 숫자로 분리한다
 *
 * `sort=LATEST`는 서버가 집계를 건너뛴다. `VIEWS|PARTICIPANTS|UNLOCKS`는 집계를 돈다.
 *
 *   집계 비용 = (VIEWS 등의 p95) − (LATEST의 p95)
 *
 * 이 차이가 곧 "Redis ZSET으로 옮기면 아낄 수 있는 시간"이고,
 * **LATEST 자체의 크기**는 집계와 무관한 비용(전체 조회·메모리 정렬)이라 ZSET으로 못 줄인다.
 * 2026-08-19 트러블슈팅에서 이 두 값을 갈라 본 것이 작업 순서를 정했다.
 *
 * ## N을 바꿔 가며 여러 번 돌린다
 *
 * 이 API는 VU보다 데이터량 N이 성능을 지배한다. N을 500 / 5,000 / 20,000으로 바꿔
 * 세 번 재면 **기울기**가 보인다 — 개선의 성공 기준은 절대값이 아니라 기울기다.
 *
 * 임계값은 일부러 안 건다 — 여기서는 재는 게 목적이고 판정은 03에서 한다.
 */
import http from 'k6/http'
import { check } from 'k6'
import { Trend } from 'k6/metrics'
import { login, useToken } from '../lib/auth.js'
import { SORTS, url } from './target.js'

export const options = {
    vus: 1,
    duration: '1m',
}

/** 정렬별 응답시간을 따로 모은다. 요약표에 네 줄로 나란히 찍힌다 */
const bySort = {}
for (const s of SORTS) bySort[s] = new Trend(`rank_${s.toLowerCase()}`, true)

export function setup() {
    return { accessToken: login() }
}

export default function (data) {
    useToken(data.accessToken)

    // 매 iteration마다 네 정렬을 같은 조건에서 나란히 — 서버 상태가 같아야 비교가 성립한다
    for (const sort of SORTS) {
        const res = http.get(url({ sort }), { tags: { sort } })
        bySort[sort].add(res.timings.duration)
        check(res, { [`${sort}: 200`]: (r) => r.status === 200 })
    }
    // think time 없음 — 표본을 최대한 많이 모은다
}

export function teardown() {
    console.log('')
    console.log('▸ 요약표의 rank_latest / rank_views / rank_participants / rank_unlocks 를 비교할 것')
    console.log('▸ (rank_views p95) − (rank_latest p95) = 랭킹 집계 비용')
    console.log('▸ rank_latest 자체 = 집계와 무관한 비용 (캐싱으로 줄지 않는다)')
    console.log('▸ 이 p95 값들로 target.js의 THRESHOLDS를 다시 정한다 (실측 × 2~3배)')
}
