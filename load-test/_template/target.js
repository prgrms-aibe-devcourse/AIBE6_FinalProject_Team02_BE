/**
 * ▣ 새 도메인 시작점 — 이 폴더를 복사해서 쓴다.
 *
 *   1) load-test/_template/ 를 load-test/<내-기능-이름>/ 으로 복사
 *   2) 이 파일의 TODO 네 곳을 채운다
 *   3) powershell -File load-test/run.ps1 -Domain <내-기능-이름> -Stage smoke
 *
 * **도메인 고유한 것은 전부 이 파일에 모은다.** 01·02·03은 실행 방식만 다르고
 * 무엇을 때릴지는 이 파일만 본다. 그래야 다음 사람이 이 파일 하나만 읽으면 된다.
 *
 * 실제 예시는 `load-test/challenge-ranking/target.js`를 볼 것.
 */
import { BASE_URL, DEFAULT_THRESHOLDS } from '../lib/config.js'

/** TODO 1 — 화면 이름. 로그·결과 파일에 찍힌다 */
export const NAME = '(내 기능 이름)'

/**
 * TODO 2 — **이 API의 성능을 지배하는 변수가 무엇인가.**
 *
 * 부하(VU)만 변수가 아니다. 여기를 잘못 짚으면 아무것도 증명하지 못한다.
 *   목록 조회  → 행 수 N        (데이터를 넣어 가며 재야 한다)
 *   이미지 업로드 → 파일 크기
 *   동시 쓰기  → 같은 행을 노리는 경쟁 정도
 *   외부 API 연동 → 그쪽 응답시간
 *
 * 데이터량이 변수라면 `seed/` 에 시드·정리 SQL을 두고 `data-count.sql`도 채운다
 */
export const DATA_NOTE = '(무엇이 성능을 좌우하는가)'

/** TODO 3 — 때릴 URL. 쿼리 파라미터가 있으면 인자로 받는다 */
export function url({ page = 0, size = 10 } = {}) {
    return `${BASE_URL}/api/v1/(내-경로)?page=${page}&size=${size}`
}

/**
 * 응답이 기대한 모양인지.
 *
 * **200만 보면 안 된다.** "200인데 목록이 비어 있다"는 상태코드로 안 잡히는 장애다.
 * 우리 서버 응답 껍데기는 `ApiResponse<T>` — 실제 데이터는 `data` 아래에 있다
 */
export function contentOk(res) {
    try {
        return res.json('data') !== null
    } catch {
        return false
    }
}

/**
 * TODO 4 — 임계값. **02-baseline으로 실측한 뒤에 채운다.**
 *
 * 처음에는 DEFAULT_THRESHOLDS를 그대로 두고 돌린다. 통과하든 실패하든 그게 정보다.
 * 실측값을 알고 나면 **실측 × 2~3배**로 조인다 — 너무 느슨하면 회귀를 못 잡고,
 * 너무 빡빡하면 정상 변동에도 빨간불이 뜬다
 */
export const THRESHOLDS = {
    ...DEFAULT_THRESHOLDS,
    // http_req_duration: ['p(95)<50', 'p(99)<150'],   // ← 실측 후 주석 해제하고 숫자 교체
}
