/**
 * 01 · 스모크 — 스크립트가 맞게 짜였는지 확인하는 30초짜리.
 *
 * 서버 성능을 재는 게 아니다. 로그인이 되는지, 시드가 들어갔는지, 응답 모양이 맞는지를 본다.
 * 부하테스트 실패의 절반은 서버가 아니라 스크립트 버그다.
 * 여기서 전부 초록이 아니면 다음 단계로 가지 말 것.
 */
import http from 'k6/http'
import { check, sleep } from 'k6'
import { login, useToken } from '../lib/auth.js'
import { NAME, contentOk, discoverMadeDexId, url } from './target.js'

export const options = {
    vus: 1,
    duration: '30s',
    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
    },
}

export function setup() {
    const accessToken = login()
    // setup의 쿠키 자에 심어야 아래 그룹 목록 조회가 인증된다
    useToken(accessToken)
    const madeDexId = discoverMadeDexId()
    console.log(`[${NAME}] 로그인 성공 · madeDexId=${madeDexId}`)
    return { accessToken, madeDexId }
}

export default function (data) {
    useToken(data.accessToken)

    const res = http.get(url({ madeDexId: data.madeDexId }))

    check(res, {
        '200': (r) => r.status === 200,
        'JSON 파싱됨': (r) => {
            try {
                return r.json() !== null
            } catch {
                return false
            }
        },
        // 카드에 썸네일이 실렸는가 = presign이 실제로 돌았는가.
        // 이게 실패하면 시드가 롤백된 것이지 서버가 느린 게 아니다
        '썸네일이 실린 카드가 있음': (r) => contentOk(r),
    })

    sleep(1)
}

export function teardown() {
    console.log('스모크 끝 — checks가 모두 100%면 02-baseline으로')
}
