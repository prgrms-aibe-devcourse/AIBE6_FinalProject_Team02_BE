/**
 * ▣ 로그잇 식탁 피드 — GET /api/v1/made-dexes/{madeDexId}/feed
 *
 * 도메인 고유한 것은 전부 이 파일에 모은다. 01·02·03은 이 파일만 본다.
 *
 * ## 왜 이 API인가
 *
 * 조회 쿼리는 이미 배치돼 있다 — MadeDexFeedService는 슬롯·멤버·기록·사진·유저를
 * 각각 한 번씩, **M·S와 무관하게 6회로 고정**해 부른다. 인덱스도
 * idx_made_dex_record_feed (made_dex_id, logged_on) 이 걸려 있다.
 *
 * 그런데도 응답을 만들 때 이미지 1건마다 S3PresignedUrlService.createDownloadUrl() 이
 * SigV4 서명을 새로 만든다. 캐시가 없어 같은 키도 매 요청 다시 서명한다.
 *
 *   서명 횟수 = M (멤버 프로필) + 기록이 있는 (슬롯 × 멤버) 칸 수  ≤ M + M×S
 *
 * 즉 이 API의 비용은 "쿼리 수"가 아니라 **응답 안의 이미지 개수**에 붙는다.
 * EXPLAIN으로도, 코드 리뷰로도, 데이터 3건인 로컬에서도 안 보인다.
 */
import http from 'k6/http'
import { fail } from 'k6'
import { BASE_URL, DEFAULT_THRESHOLDS } from '../lib/config.js'

/** 1 — 화면 이름 */
export const NAME = '로그잇 식탁 피드'

/**
 * 2 — **이 API의 성능을 지배하는 변수**
 *
 * VU가 아니라 **요청 1건이 만들어 내는 서명 횟수**다. 그 값은 멤버 수 M과 슬롯 수 S가 정한다.
 * seed-feed.sql이 M·S를 정확히 만들고, data-count.sql이 그 서명 횟수를 센다.
 *
 * 기록당 사진 수 p는 이 값에 영향을 주지 않는다 — toCard()가 카드당 대표 1장만 서명한다.
 * p는 "조회되는 행 수"만 늘리므로, p를 올려도 안 느려지면 병목이 DB가 아니라는 증거가 된다.
 */
export const DATA_NOTE =
    '요청당 presigned URL 서명 횟수 = 멤버 M + 기록이 있는 (슬롯×멤버) 칸 수 (≤ M + M×S)'

/** 시드가 만드는 그룹 이름. discoverMadeDexId()가 이걸로 찾는다 */
export const GROUP_NAME_PREFIX = 'LOADTEST-FEED'

/**
 * 3 — 때릴 URL.
 *
 * madeDexId는 시드가 만든 그룹의 id라 실행할 때마다 달라진다. 사람이 손으로 넘기지 않고
 * setup()에서 discoverMadeDexId()가 찾아서 각 VU에 넘긴다.
 *
 * date를 안 주면 서버가 오늘(Asia/Seoul)로 본다 — 시드도 오늘 날짜로 넣는다.
 * 자정을 넘겨 다시 재려면 시드를 다시 돌릴 것. 어제 데이터를 그대로 쓰려면 DATE=YYYY-MM-DD.
 */
export function url({ madeDexId, date = __ENV.DATE || '' } = {}) {
    if (!madeDexId) {
        fail('madeDexId가 비어 있다. setup()에서 discoverMadeDexId()를 부르고 결과를 넘길 것')
    }
    return `${BASE_URL}/api/v1/made-dexes/${madeDexId}/feed${date ? `?date=${date}` : ''}`
}

/**
 * 시드가 만든 그룹의 id를 찾는다. **setup()에서 로그인 직후 1회만** 부른다.
 *
 * 내 그룹 목록(GET /api/v1/made-dexes)에서 이름이 LOADTEST-FEED로 시작하는 것을 고른다.
 * 리뷰어 계정이 그 그룹의 멤버여야 보인다 — MadeDexFinder.readable()이 멤버만 통과시키고,
 * 멤버가 아니면 403이 아니라 **404**로 답한다(존재 사실을 숨기는 설계).
 * 그래서 "404가 뜬다 = 경로가 틀렸다"로 읽으면 안 되고, 시드부터 확인해야 한다.
 */
export function discoverMadeDexId() {
    const res = http.get(`${BASE_URL}/api/v1/made-dexes`)
    if (res.status !== 200) {
        fail(`내 그룹 목록 조회 실패 status=${res.status}. 로그인이 됐는지 먼저 볼 것`)
    }

    let list
    try {
        list = res.json('data')
    } catch {
        fail(`그룹 목록 응답이 JSON이 아님: ${res.body}`)
    }
    if (!Array.isArray(list)) {
        fail(`그룹 목록이 배열이 아님: ${JSON.stringify(list)}`)
    }

    const found = list.find((g) => g.name && g.name.startsWith(GROUP_NAME_PREFIX))
    if (!found) {
        fail(
            `${GROUP_NAME_PREFIX}로 시작하는 그룹이 없다. 시드를 먼저 넣을 것:\n` +
                '  docker exec -i catcheat-postgres psql -U catcheat -d catcheat_dev -v m=12 -v s=4 -v p=1 ' +
                '< load-test/logit-feed/seed/seed-feed.sql\n' +
                `  (지금 보이는 그룹: ${list.map((g) => g.name).join(', ') || '(없음)'})`
        )
    }
    return found.id
}

/**
 * 응답이 기대한 모양인가.
 *
 * **200만 보면 안 된다.** 시드가 롤백돼서 "200인데 카드가 비어 있다"면 아무리 빨라도
 * 그 숫자는 아무것도 증명하지 못한다 — 서명이 한 번도 안 돌았다는 뜻이기 때문이다.
 * 그래서 썸네일이 실제로 실렸는지까지 본다.
 */
export function contentOk(res) {
    try {
        const data = res.json('data')
        if (!data || !Array.isArray(data.slots) || data.slots.length === 0) return false
        // 썸네일이 붙은 카드가 한 장이라도 있어야 한다 (presign이 실제로 돈 것)
        return data.slots.some((slot) =>
            (slot.cards || []).some((card) => card.thumbnailUrl)
        )
    } catch {
        return false
    }
}

/**
 * 4 — 임계값. **02-baseline 실측 후 채운다.**
 *
 * 지금은 DEFAULT_THRESHOLDS(p95<500)로 둔다. 통과하든 실패하든 그게 첫 정보다.
 * 실측하면 실측 × 2~3배로 조이고, **그때 서명 횟수가 몇이었는지 함께 적는다** —
 * 같은 코드가 서명 10회에서 8ms, 60회에서 300ms가 될 수 있어 조건 없는 임계값은 의미가 없다.
 */
export const THRESHOLDS = {
    ...DEFAULT_THRESHOLDS,
    // http_req_duration: ['p(95)<XX', 'p(99)<XX'],   // ← baseline(서명 ___회) 실측 후 교체
}
