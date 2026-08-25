#!/usr/bin/env bash
# 부하테스트 실행기 (macOS · Linux · Git Bash) — run.ps1과 같은 일을 한다
#
#   bash load-test/run.sh --stage smoke
#   bash load-test/run.sh --stage baseline
#   bash load-test/run.sh --stage load --vus 150 --tag before
#   bash load-test/run.sh --domain my-feature --stage smoke
#
# 실행 권한을 주면 ./load-test/run.sh 로도 된다:
#   chmod +x load-test/run.sh
#
# ## 도메인 폴더 규약 (run.ps1과 동일)
#
# 이 러너는 도메인을 모른다. `load-test/<domain>/` 안에서 아래 이름을 찾아 쓴다.
#
#   01-smoke.js · 02-baseline.js · 03-load.js   (필수)
#   data-count.sql                              (선택 — 있으면 데이터량을 세서 알려준다)
#   seed/                                       (선택 — 시드·정리 SQL)
#
# ## 왜 jq를 쓰지 않나
#
# jq가 안 깔린 환경이 흔해서 의존성을 만들지 않았다. Prometheus 응답에서 값 하나만
# sed로 뽑는다. 우리가 쓰는 질의는 전부 단일 값으로 집계되므로 이걸로 충분하다.

set -uo pipefail

DOMAIN='challenge-ranking'
STAGE='load'
VUS=150
SORT='VIEWS'
TAG='before'
TAG_EXPLICIT=0
BASE_URL='http://localhost:8080'

PG_CONTAINER='catcheat-postgres'
PG_USER='catcheat'
PG_DB='catcheat_dev'
PROM='http://localhost:9090'
GRAFANA='http://localhost:3001'

while [ $# -gt 0 ]; do
    case "$1" in
        --domain)   DOMAIN="$2"; shift 2 ;;
        --stage)    STAGE="$2"; shift 2 ;;
        --vus)      VUS="$2"; shift 2 ;;
        --sort)     SORT="$2"; shift 2 ;;
        --tag)      TAG="$2"; TAG_EXPLICIT=1; shift 2 ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *)
            echo "알 수 없는 인자: $1" >&2
            echo "사용법은 --help" >&2
            exit 1 ;;
    esac
done

case "$STAGE" in
    smoke)    SCRIPT='01-smoke.js' ;;
    baseline) SCRIPT='02-baseline.js' ;;
    load)     SCRIPT='03-load.js' ;;
    *) echo "--stage 는 smoke | baseline | load 중 하나여야 한다 (받은 값: $STAGE)" >&2; exit 1 ;;
esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOMAIN_DIR="$ROOT/load-test/$DOMAIN"
SCRIPT_PATH="$DOMAIN_DIR/$SCRIPT"

if [ ! -f "$SCRIPT_PATH" ]; then
    echo ""
    echo "$SCRIPT_PATH 가 없습니다."
    echo "있는 도메인:"
    for d in "$ROOT"/load-test/*/; do
        name="$(basename "$d")"
        case "$name" in lib|results|_template) continue ;; esac
        echo "  - $name"
    done
    echo "새로 만들려면 load-test/_template 를 복사하세요."
    exit 1
fi

# k6 찾기. macOS(brew)·Linux는 PATH에 있다.
# Git Bash·WSL은 winget으로 깐 뒤 새 터미널을 열지 않으면 PATH에 안 잡히므로 기본 설치 경로를 본다
K6="$(command -v k6 || true)"
if [ -z "$K6" ]; then
    for cand in '/c/Program Files/k6/k6.exe' '/mnt/c/Program Files/k6/k6.exe'; do
        [ -x "$cand" ] && { K6="$cand"; break; }
    done
fi
if [ -z "$K6" ]; then
    echo "k6를 찾을 수 없습니다." >&2
    echo "  macOS   brew install k6" >&2
    echo "  Linux   https://k6.io/docs/get-started/installation" >&2
    echo "  Windows winget install --id GrafanaLabs.k6 -e  (설치 후 새 터미널)" >&2
    exit 1
fi

# ── 사전 점검 ────────────────────────────────────────────────────────────────
echo ""
echo "=== PREFLIGHT ==="
OK=1

# 본문을 버릴 곳.
#
# Git Bash의 MinGW curl은 `-o /dev/null`을 주면 **상태코드는 출력하고 exit 23(write error)**로
# 끝난다. 그래서 `|| echo 0`을 붙여 두면 `302` 뒤에 `0`이 붙어 `3020`이 되고, 멀쩡한 서버가
# FAIL로 판정됐다. Windows에서는 예약 장치명 `NUL`을 써야 한다
case "$(uname -s 2>/dev/null || echo unknown)" in
    MINGW*|MSYS*|CYGWIN*) DEVNULL='NUL';       CYGPATH="$(command -v cygpath || true)" ;;
    *)                    DEVNULL='/dev/null'; CYGPATH='' ;;
esac

# Windows용 k6.exe는 `/d/경로/...` 같은 POSIX 경로를 못 읽는다
# ("The moduleSpecifier couldn't be found on local disk"). Git Bash에서만 변환한다
to_native() {
    if [ -n "$CYGPATH" ]; then "$CYGPATH" -w "$1"; else printf '%s' "$1"; fi
}

probe() { # label url expect
    local label="$1" url="$2" expect="$3" code
    # 리다이렉트는 따라가지 않는다 — 302를 302로 받아야 한다.
    # 실패 시 `|| code=0`으로 **덮어쓴다**(`|| echo 0`은 출력에 덧붙어 값이 망가진다)
    code="$(curl -s -o "$DEVNULL" -w '%{http_code}' --max-time 5 "$url")" || code=0
    if [ "$code" = "$expect" ]; then
        printf '  [OK  ] %-22s HTTP %s\n' "$label" "$code"
    else
        printf '  [FAIL] %-22s HTTP %s (기대 %s)\n' "$label" "$code" "$expect"
        OK=0
    fi
}

probe 'backend :8080'    "$BASE_URL/api/v1/challenges"           302
probe 'metrics :9091'    'http://localhost:9091/actuator/prometheus' 200
probe 'prometheus :9090' "$PROM/-/healthy"                       200

# Prometheus 응답에서 값 하나만 뽑는다 — "value":[<시각>,"<값>"]
prom_value() {
    curl -s --max-time 10 --get --data-urlencode "query=$1" "$PROM/api/v1/query" \
        | sed -n 's/.*"value":\[[^,]*,"\([^"]*\)".*/\1/p' | head -1
}

UP="$(prom_value 'up{job="catcheat-backend"}')"
if [ "$UP" = "1" ]; then
    printf '  [OK  ] %-22s up=1\n' 'scrape target'
else
    printf '  [FAIL] %-22s up=%s\n' 'scrape target' "${UP:-조회 실패}"
    OK=0
fi

# FAIL 판정을 데이터량 조회보다 **먼저** 낸다.
# 서버·프로메테우스가 없으면 데이터량을 세어 봐야 의미가 없고, docker까지 꺼져 있으면
# 조회가 실패하며 화면만 지저분해진다. (run.ps1은 같은 자리에서 아예 죽었다)
if [ "$OK" -ne 1 ]; then
    echo ""
    echo "사전 점검 실패. 위 FAIL 항목을 해결하고 다시 실행하세요."
    exit 1
fi

# 데이터량 — 도메인이 data-count.sql을 두면 세어서 알려준다.
#
# 부하(VU)만 성능 변수가 아니다. 목록 조회는 행 수가, 업로드는 파일 크기가 지배한다.
# 데이터가 3건인 DB에 VU 200을 때려도 "빠르다"만 나오고 아무것도 증명하지 못한다.
#
# 도메인이 `data-count.sql` 첫머리 주석으로 아래 셋을 선언할 수 있다. **전부 선택 사항**이고,
# 없으면 예전 기본값(하한 100 · 시드 인자 `-v n=5000`)을 그대로 쓴다 — 기존 도메인은 동작이 안 바뀐다.
#
#   -- floor: 10                        이 값 미만일 때만 경고한다
#   -- seed:  -v m=12 -v s=4 -v p=1     경고와 함께 안내할 시드 인자
#   -- unit:  요청당 presign 서명 횟수    숫자가 무엇의 개수인지
#
# 실행기는 이 숫자가 무엇인지 모른다. 챌린지 랭킹은 "행 수"라 100 미만이면 시딩을 안 한 게
# 맞지만, 로그잇 피드는 "요청당 서명 횟수"라 60이 사실상 상한이다. 판단 기준은 도메인이 안다.
N=''
UNIT=''
UNIT_PART=''   # set -u 아래에서는 미초기화 변수를 읽는 순간 죽는다. 아래 결과 파일 헤더가 이걸 쓴다
COUNT_SQL="$DOMAIN_DIR/data-count.sql"
if [ -f "$COUNT_SQL" ]; then
    FLOOR=100
    SEED_ARGS='-v n=5000'
    # CRLF로 저장된 파일에서도 값 끝에 \r가 붙지 않게 지운다
    META="$(tr -d '\r' < "$COUNT_SQL")"
    v="$(printf '%s\n' "$META" | sed -n 's/^[[:space:]]*--[[:space:]]*floor:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
    [ -n "$v" ] && FLOOR="$v"
    v="$(printf '%s\n' "$META" | sed -n 's/^[[:space:]]*--[[:space:]]*seed:[[:space:]]*\(.*[^[:space:]]\)[[:space:]]*$/\1/p' | head -1)"
    [ -n "$v" ] && SEED_ARGS="$v"
    v="$(printf '%s\n' "$META" | sed -n 's/^[[:space:]]*--[[:space:]]*unit:[[:space:]]*\(.*[^[:space:]]\)[[:space:]]*$/\1/p' | head -1)"
    [ -n "$v" ] && UNIT="$v"

    N="$(docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -t -A 2>/dev/null < "$COUNT_SQL" | head -1 | tr -d '[:space:]')"
    case "$N" in ''|*[!0-9]*) N='?' ;; esac
    if [ -n "$UNIT" ]; then UNIT_PART=" ($UNIT)"; else UNIT_PART=''; fi
    # 데이터량은 알면 좋은 정보이지 실행 조건이 아니다. 못 세면 건너뛰고 측정은 계속한다
    if [ "$N" = '?' ]; then
        printf '  [WARN] %-22s = ? (조회 실패 — 데이터량 점검을 건너뜁니다)\n' '데이터량'
    else
        printf '  [INFO] %-22s = %s%s\n' '데이터량' "$N" "$UNIT_PART"
        if [ "$N" -lt "$FLOOR" ] && [ "$STAGE" != 'smoke' ]; then
            echo "         경고: 데이터가 적어 부하를 걸어도 병목이 안 나타납니다. (하한 $FLOOR)"
            for s in "$DOMAIN_DIR"/seed/seed-*.sql; do
                [ -f "$s" ] || continue
                echo "         docker exec -i $PG_CONTAINER psql -U $PG_USER -d $PG_DB $SEED_ARGS < load-test/$DOMAIN/seed/$(basename "$s")"
            done
        fi
    fi
else
    printf '  [INFO] %-22s (data-count.sql 없음 — 건너뜀)\n' '데이터량'
fi

# ── 실행 ─────────────────────────────────────────────────────────────────────
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$ROOT/load-test/results"
mkdir -p "$OUT_DIR"
# 태그는 **전후를 가르는 표시**다. 그래서 명시했을 때만 파일명에 넣는다.
#
# 예전에는 기본값 'before'가 단계와 무관하게 항상 들어갔다. 그래서 기준선을 잴 때
# --tag를 안 넘기면 **개선 후에 잰 파일도 이름이 `before`**가 됐다.
# load는 예전부터 기본값이 파일명에 들어갔으므로 그대로 둔다 — 기존 도메인의 파일명이 바뀌지 않는다
if [ "$TAG_EXPLICIT" -eq 1 ] || [ "$STAGE" = 'load' ]; then
    NAME_PART="$DOMAIN-$TAG-$STAGE"
    TAG_SHOWN="$TAG"
else
    NAME_PART="$DOMAIN-$STAGE"
    TAG_SHOWN='(없음)'
fi
OUT_FILE="$OUT_DIR/$NAME_PART-$STAMP.txt"

# k6 자체 설정은 환경변수로만 받는다 (CLI 플래그가 없다)
export K6_PROMETHEUS_RW_SERVER_URL="$PROM/api/v1/write"
export K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max'

STARTED_AT="$(date +%s)"

echo ""
echo "=== RUN ==="
# SORT는 찍지 않는다 — 쓰는 도메인만 의미가 있고, 그 도메인의 setup()이 스스로 로그를 남긴다
if [ -n "$N" ]; then DATA_PART="데이터량=$N"; else DATA_PART='데이터량=(해당 없음)'; fi
echo "  domain=$DOMAIN  stage=$STAGE  VUS=$VUS  $DATA_PART  tag=$TAG_SHOWN"
echo "  결과 파일: $OUT_FILE"
echo ""

# 스크립트 값은 -e 로 명시 전달 — 셸 환경변수에 의존하지 않는다.
# stderr는 합치지 않는다(k6의 진행 로그는 터미널로, 요약표는 stdout이라 파일에 다 들어온다)
"$K6" run --no-usage-report \
    -o experimental-prometheus-rw \
    -e "VUS=$VUS" -e "SORT=$SORT" -e "BASE_URL=$BASE_URL" \
    "$(to_native "$SCRIPT_PATH")" | tee "$OUT_FILE"
K6_EXIT="${PIPESTATUS[0]}"

# 측정 조건을 파일 맨 앞에 남긴다.
#
# 예전에는 조건이 콘솔에만 찍혀서, 며칠 뒤 결과 파일만 열면 M이 몇일 때 잰 값인지 알 수 없었다.
# 같은 코드가 서명 10회에서 58ms, 60회에서 308ms가 되므로 조건 없는 수치는 근거가 되지 못한다
{
    echo "# domain=$DOMAIN  stage=$STAGE  VUS=$VUS  tag=$TAG_SHOWN"
    if [ -n "$N" ]; then echo "# 데이터량=$N$UNIT_PART"; else echo '# 데이터량=(해당 없음)'; fi
    echo "# BASE_URL=$BASE_URL  실행=$STAMP"
    echo ""
    cat "$OUT_FILE"
} > "$OUT_FILE.tmp" && mv -f "$OUT_FILE.tmp" "$OUT_FILE"

# 99만 "임계값 미달"이다. 나머지 비정상 종료는 측정 실패이므로 구분해서 알려야 한다
# (예전에는 전부 "임계값 미달"로 찍혀서, 스크립트를 못 찾은 127도 정상 완료처럼 보였다)
if [ "$K6_EXIT" -eq 99 ]; then
    echo "  (k6 종료코드 99 — 임계값 미달. 측정은 정상 완료)"
elif [ "$K6_EXIT" -ne 0 ]; then
    echo ""
    echo "  ⚠️ k6가 실패했습니다 (종료코드 $K6_EXIT). 위 오류를 먼저 해결하세요."
    echo "     아래 지표는 측정된 것이 아닙니다."
fi

ENDED_AT="$(date +%s)"

# ── 서버 내부 지표 요약 ──────────────────────────────────────────────────────
sleep 6 # 마지막 scrape 반영 대기
WIN="$((ENDED_AT - STARTED_AT + 30))s"

show() { # 라벨 질의 [배수]
    local label="$1" expr="$2" mul="${3:-1}" v
    v="$(prom_value "$expr")"
    if [ -z "$v" ]; then
        printf '  %-18s %s\n' "$label" '(없음)'
        return
    fi
    v="$(awk -v x="$v" -v m="$mul" 'BEGIN{printf "%.2f", x*m}')"
    printf '  %-18s %s\n' "$label" "$v"
    echo "$label = $v" >> "$OUT_FILE"
}

echo ""
echo "=== 서버 내부 지표 (테스트 구간 최대) ==="
J='{job="catcheat-backend"}'
show '최대 VU'           "max_over_time(k6_vus[$WIN])"
show 'RPS 최대'          "max_over_time(sum(rate(http_server_requests_seconds_count$J[30s]))[$WIN:5s])"
# uri 필터를 걸지 않는다 — 도메인마다 경로가 다르다. 테스트가 한 엔드포인트만 때리므로 전체 p95면 충분하다
show '서버 p95 최대(ms)' "max_over_time(histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket$J[1m])))[$WIN:5s])" 1000
show '커넥션 사용 최대'  "max_over_time(hikaricp_connections_active[$WIN])"
show '커넥션 대기 최대'  "max_over_time(hikaricp_connections_pending[$WIN])"
show '획득 대기(ms)'     "max_over_time((rate(hikaricp_connections_acquire_seconds_sum[1m])/rate(hikaricp_connections_acquire_seconds_count[1m]))[$WIN:5s])" 1000
show '톰캣 busy 최대'    "max_over_time(tomcat_threads_busy_threads[$WIN])"
show 'CPU 최대(%)'       "max_over_time(process_cpu_usage[$WIN])" 100

# ── Grafana 링크 ─────────────────────────────────────────────────────────────
# k6 종료 후에도 remote write 시리즈는 stale 표시가 없어 마지막 값이 약 5분 유지된다.
# 그래서 끝 시각을 k6_vus에서 찾지 않고 실제 실행 시각을 쓴다
echo ""
echo "=== GRAFANA (캡쳐용 · 구간 자동 지정) ==="
echo "  $GRAFANA/d/catcheat-loadtest/catcheat?from=$(((STARTED_AT - 30) * 1000))&to=$(((ENDED_AT + 30) * 1000))"
echo ""
