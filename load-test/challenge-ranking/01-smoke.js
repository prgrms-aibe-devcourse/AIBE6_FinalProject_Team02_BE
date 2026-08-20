/**
 * 01 · 스모크 — **스크립트가 맞게 짜였는지** 확인하는 30초짜리
 *
 *   powershell -File load-test/run.ps1 -Stage smoke
 *
 * 서버 성능을 재는 게 아니다. 로그인이 되는지, 경로가 맞는지, 응답이 기대한 모양인지를 본다.
 * **부하테스트 실패의 절반은 서버가 아니라 스크립트 버그**(토큰 만료·경로 오타·JSON 파싱)라
 * 이걸 건너뛰면 5분짜리 테스트를 통째로 날린다.
 *
 * 여기서 전부 초록이 아니면 다음 단계로 가지 말 것.
 */
import http from 'k6/http'
import { check, sleep } from 'k6'
import { login, useToken } from '../lib/auth.js'
import { NAME, SORTS, contentOk, url } from './target.js'

export const options = {
    vus: 1,
    duration: '30s',
    // 스모크는 성능이 아니라 정합성을 본다 — 느슨하게 두고 check만 본다
    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
    },
}

export function setup() {
    const accessToken = login()
    console.log(`[${NAME}] 로그인 성공 — 토큰 확보`)
    return { accessToken }
}

export default function (data) {
    useToken(data.accessToken)

    for (const sort of SORTS) {
        const res = http.get(url({ sort }), { tags: { sort } })

        check(res, {
            [`${sort}: 200`]: (r) => r.status === 200,
            [`${sort}: JSON 파싱됨`]: (r) => {
                try {
                    return r.json() !== null
                } catch {
                    return false
                }
            },
            [`${sort}: content 배열 있음`]: (r) => contentOk(r),
        })
    }

    sleep(1)
}

export function teardown() {
    console.log('스모크 끝 — 위 checks가 모두 100%면 02-baseline으로')
}
