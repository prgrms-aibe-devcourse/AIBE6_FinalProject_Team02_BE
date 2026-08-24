/**
 * 02 · 기준선 — **부하 없이(VU 1)** 우리 서버가 낼 수 있는 최선값을 잰다
 *
 * 부하가 섞이면 "원래 느린 건지, 몰려서 느린 건지" 구분이 안 된다.
 * 순수 처리 시간을 먼저 알아야 03의 임계값을 정할 수 있다. **모든 기준의 출발점.**
 *
 * ## 이 도메인에서는 반드시 M을 바꿔 가며 여러 번 돌린다
 *
 * 피드의 지배 변수는 요청당 서명 횟수이고, 그 값은 멤버 수 M이 정한다.
 * 한 번만 재면 점 하나뿐이라 "느리다/빠르다"밖에 말할 수 없다. 세 번 재면 **기울기**가 보인다.
 *
 *   # M=2  (서명 2 + 2×4 = 10회)
 *   docker exec -i catcheat-postgres psql -U catcheat -d catcheat_dev -v m=2  -v s=4 -v p=1 < load-test/logit-feed/seed/seed-feed.sql
 *   .\load-test\run.ps1 -Domain logit-feed -Stage baseline
 *
 *   # M=6  (서명 6 + 6×4 = 30회)   ← 시드만 다시, 명령은 동일
 *   # M=12 (서명 12 + 12×4 = 60회)
 *
 * 개선의 성공 기준은 절대값("빨라졌다")이 아니라 기울기("M이 늘어도 안 느려진다")다.
 *
 * ## p(사진 수)를 올려 보는 것도 한 번은 해 둘 것
 *
 * p를 1 → 8로 올리면 조회되는 행은 8배가 되지만 **서명 횟수는 그대로**다
 * (toCard가 카드당 대표 1장만 서명한다). 여기서 응답시간이 거의 안 변하면
 * "병목은 DB가 아니다"가 측정으로 증명된다 — 개선 방향을 정하는 근거가 된다.
 *
 * 임계값은 걸지 않는다 — 여기서는 재는 게 목적이고 판정은 03에서 한다.
 */
import http from 'k6/http'
import { check } from 'k6'
import { Trend } from 'k6/metrics'
import { login, useToken } from '../lib/auth.js'
import { NAME, contentOk, discoverMadeDexId, url } from './target.js'

export const options = {
    vus: 1,
    duration: '1m',
}

/** 실행마다 M이 달라지므로 이름은 하나로 두고, 결과 파일의 M 표기로 구분한다 */
const feedLatency = new Trend('logit_feed', true)

export function setup() {
    const accessToken = login()
    useToken(accessToken)
    const madeDexId = discoverMadeDexId()
    console.log(`[${NAME}] madeDexId=${madeDexId} — PREFLIGHT가 찍은 "요청당 서명 횟수"를 함께 적어 둘 것`)
    return { accessToken, madeDexId }
}

export default function (data) {
    useToken(data.accessToken)

    const res = http.get(url({ madeDexId: data.madeDexId }))
    feedLatency.add(res.timings.duration)

    check(res, {
        '200': (r) => r.status === 200,
        '썸네일이 실린 카드가 있음': (r) => contentOk(r),
    })
    // think time 없음 — 표본을 최대한 많이 모은다
}

export function teardown() {
    console.log('')
    console.log('▸ 요약표의 p95와 PREFLIGHT의 서명 횟수를 한 줄로 적어 둔다: "M=__ · 서명 __회 · p95 __ms"')
    console.log('▸ M을 2 / 6 / 12로 바꿔 세 번 재야 기울기가 나온다. 점 하나로는 개선을 주장할 수 없다')
}
