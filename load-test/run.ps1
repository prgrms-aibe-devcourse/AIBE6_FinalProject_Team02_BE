# 부하테스트 실행기 — 사전 점검 · k6 실행 · 서버 지표 요약 · Grafana 링크를 한 번에
#
#   .\load-test\run.ps1 -Stage smoke
#   .\load-test\run.ps1 -Stage baseline
#   .\load-test\run.ps1 -Stage load -Vus 150 -Tag before
#   .\load-test\run.ps1 -Domain my-feature -Stage smoke      ← 다른 기능을 잴 때
#
# Git Bash에서는:
#   powershell -ExecutionPolicy Bypass -File load-test/run.ps1 -Stage smoke
#
# 환경변수를 셸에 남기지 않고 스크립트 안에서 k6에 직접 넘긴다.
# ($env:VUS 를 따로 세팅하는 방식은 다른 터미널에서 실행하면 조용히 기본값으로 돌아간다)
#
# ## 도메인 폴더 규약
#
# 이 러너는 도메인을 모른다. `load-test/<Domain>/` 안에서 아래 이름을 찾아 쓴다.
#
#   01-smoke.js · 02-baseline.js · 03-load.js   (필수)
#   data-count.sql                              (선택 — 있으면 데이터량을 세서 알려준다)
#   seed/                                       (선택 — 시드·정리 SQL)
#
# 새 기능을 재려면 `load-test/_template/`를 복사해 이름만 바꾸면 된다.

param(
    [ValidateSet('smoke', 'baseline', 'load')]
    [string]$Stage = 'load',
    # 측정할 도메인 = load-test 하위 폴더 이름
    [string]$Domain = 'challenge-ranking',
    [int]$Vus = 150,
    # 도메인이 SORT를 쓰지 않으면 무시된다 (k6 스크립트가 __ENV.SORT를 안 읽으면 그만)
    [string]$Sort = 'VIEWS',
    [string]$Tag = 'before',
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'

# 콘솔 입출력을 UTF-8로 고정한다.
#
# Windows PowerShell 5.1의 기본 콘솔 인코딩은 OEM 코드페이지(한국 윈도우는 949)다.
# k6는 UTF-8로 쓰기 때문에 그대로 두면 k6가 찍는 한글이 `로그???�공`처럼 깨지고,
# Git Bash로 파이프할 때 이 스크립트의 한글도 깨진다.
#
# (스크립트 파일 자체는 **UTF-8 BOM으로 저장**해야 한다 — BOM이 없으면 5.1이 파일을
#  ANSI로 파싱해서 문자열 리터럴이 깨진다. 이 줄로는 그건 못 고친다)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$k6 = 'C:\Program Files\k6\k6.exe'
if (-not (Test-Path $k6)) { $k6 = 'k6' }

$script = switch ($Stage) {
    'smoke' { '01-smoke.js' }
    'baseline' { '02-baseline.js' }
    'load' { '03-load.js' }
}

$domainDir = Join-Path $root "load-test\$Domain"
$scriptPath = Join-Path $domainDir $script
if (-not (Test-Path $scriptPath)) {
    Write-Host "`n$scriptPath 가 없습니다." -ForegroundColor Red
    Write-Host '있는 도메인:' -ForegroundColor Yellow
    Get-ChildItem (Join-Path $root 'load-test') -Directory |
        Where-Object { $_.Name -notin @('lib', 'results', '_template') } |
        ForEach-Object { Write-Host "  - $($_.Name)" }
    Write-Host '새로 만들려면 load-test\_template 를 복사하세요.' -ForegroundColor Yellow
    exit 1
}

# --- 사전 점검 -------------------------------------------------------------
Write-Host "`n=== PREFLIGHT ===" -ForegroundColor Cyan
$ok = $true

function Probe($label, $url, $expect) {
    # Invoke-WebRequest를 쓰지 않는다.
    #
    # Windows PowerShell 5.1에서 `-MaximumRedirection 0`은 302를 만나면 예외를 던지는데,
    # 그 예외에는 `.Exception.Response`가 없어서 상태코드를 잃는다 → HTTP 0 → 멀쩡한
    # 서버를 FAIL로 판정했다. PowerShell 7에서는 그렇지 않아 환경에 따라 결과가 갈렸다.
    #
    # HttpWebRequest는 AllowAutoRedirect=false면 302를 예외 없이 그대로 돌려주므로
    # 5.1과 7에서 똑같이 동작한다
    $code = 0
    try {
        $req = [System.Net.WebRequest]::Create($url)
        $req.Method = 'GET'
        $req.AllowAutoRedirect = $false
        $req.Timeout = 5000
        $res = $req.GetResponse()
        $code = [int]$res.StatusCode
        $res.Close()
    }
    catch [System.Net.WebException] {
        # 4xx·5xx는 여기로 온다 — 응답이 붙어 있으면 상태코드를 꺼낸다
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    }
    catch {
        # 연결 자체가 안 됨(서버 미기동 등) — 0으로 남긴다
    }
    $pass = $code -eq $expect
    $mark = if ($pass) { 'OK  ' } else { 'FAIL' }
    Write-Host ("  [{0}] {1,-22} HTTP {2}" -f $mark, $label, $code)
    return $pass
}

$ok = (Probe 'backend :8080' "$BaseUrl/api/v1/challenges" 302) -and $ok
$ok = (Probe 'metrics :9091' 'http://localhost:9091/actuator/prometheus' 200) -and $ok
$ok = (Probe 'prometheus :9090' 'http://localhost:9090/-/healthy' 200) -and $ok

# Prometheus가 백엔드를 실제로 긁고 있나
try {
    $t = Invoke-RestMethod 'http://localhost:9090/api/v1/query?query=up{job="catcheat-backend"}' -TimeoutSec 5
    $up = $t.data.result[0].value[1]
    $pass = $up -eq '1'
    Write-Host ("  [{0}] {1,-22} up={2}" -f $(if ($pass) { 'OK  ' } else { 'FAIL' }), 'scrape target', $up)
    $ok = $pass -and $ok
}
catch { Write-Host '  [FAIL] scrape target        (조회 실패)'; $ok = $false }

# 데이터량 — 도메인이 data-count.sql을 두면 세어서 알려준다.
#
# 부하(VU)만 성능 변수가 아니다. 목록 조회는 행 수가, 업로드는 파일 크기가 지배한다.
# 데이터가 3건인 DB에 VU 200을 때려도 "빠르다"만 나오고 아무것도 증명하지 못한다.
#
# SQL은 인자 대신 stdin으로 넘긴다 — 부등호·괄호가 섞인 문자열을 네이티브 인자로 넘기면
# PowerShell이 조용히 망가뜨리는 경우가 있다
$n = $null
$countSql = Join-Path $domainDir 'data-count.sql'
if (Test-Path $countSql) {
    # 2>$null — native stderr를 삼킨다. `2>&1`로 합치면 5.1이 ErrorRecord로 감싸고
    # $ErrorActionPreference='Stop'과 만나 스크립트가 죽는다
    $raw = (Get-Content -Raw $countSql |
        docker exec -i catcheat-postgres psql -U catcheat -d catcheat_dev -t -A 2>$null) |
        Select-Object -First 1
    $n = "$raw".Trim()
    if (-not $n -or $n -notmatch '^\d+$') { $n = '?' }
    Write-Host ("  [INFO] {0,-22} = {1}" -f '데이터량', $n)
    if ($n -ne '?' -and [int]$n -lt 100 -and $Stage -ne 'smoke') {
        Write-Host '         경고: 데이터가 적어 부하를 걸어도 병목이 안 나타납니다.' -ForegroundColor Yellow
        $seedDir = Join-Path $domainDir 'seed'
        if (Test-Path $seedDir) {
            Get-ChildItem $seedDir -Filter 'seed-*.sql' | ForEach-Object {
                Write-Host ("         docker exec -i catcheat-postgres psql -U catcheat -d catcheat_dev -v n=5000 < load-test/$Domain/seed/$($_.Name)") -ForegroundColor Yellow
            }
        }
    }
}
else {
    Write-Host ("  [INFO] {0,-22} (data-count.sql 없음 — 건너뜀)" -f '데이터량')
}

if (-not $ok) {
    Write-Host "`n사전 점검 실패. 위 FAIL 항목을 해결하고 다시 실행하세요." -ForegroundColor Red
    exit 1
}

# --- 실행 -----------------------------------------------------------------
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $root 'load-test\results'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$outFile = Join-Path $outDir "$Domain-$Tag-$Stage-$stamp.txt"

# k6 자체 설정은 환경변수로만 받는다 (CLI 플래그가 없음)
$env:K6_PROMETHEUS_RW_SERVER_URL = 'http://localhost:9090/api/v1/write'
$env:K6_PROMETHEUS_RW_TREND_STATS = 'p(95),p(99),avg,max'

$startedAt = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

Write-Host "`n=== RUN ===" -ForegroundColor Cyan
# SORT는 찍지 않는다 — 쓰는 도메인만 의미가 있고, 그 도메인의 setup()이 스스로 로그를 남긴다.
# 안 쓰는 도메인에서 `SORT=VIEWS`가 보이면 "내가 뭘 잘못 설정했나"로 읽힌다
$dataPart = if ($n) { "데이터량=$n" } else { '데이터량=(해당 없음)' }
Write-Host "  domain=$Domain  stage=$Stage  VUS=$Vus  $dataPart  tag=$Tag"
Write-Host "  결과 파일: $outFile`n"

# 스크립트 값은 -e 로 명시 전달 — 셸 환경변수에 의존하지 않는다
#
# ⚠️ `2>&1`을 붙이지 않는다.
#
# k6는 진행 로그(`level=info ...`)를 stderr로 쓴다. PS 5.1에서 native 명령의 stderr를
# `2>&1`로 성공 스트림에 합치면 **각 줄이 ErrorRecord로 감싸이고**, 위의
# `$ErrorActionPreference='Stop'`과 만나 NativeCommandError로 스크립트가 그 자리에서 죽는다.
# (k6는 정상 동작 중이었는데도 요약표를 보기 전에 중단됐다)
#
# 우리가 파일에 남겨야 하는 요약표는 **stdout**이다. 합치지 않아도 다 들어온다.
# k6의 stderr 로그는 터미널에 그대로 흐른다
& $k6 run --no-usage-report `
    -o experimental-prometheus-rw `
    -e VUS=$Vus -e SORT=$Sort -e BASE_URL=$BaseUrl `
    $scriptPath | Tee-Object -FilePath $outFile

# 99만 "임계값 미달"이다. 나머지 비정상 종료는 측정 실패이므로 구분해서 알려야 한다
# (전부 "임계값 미달"로 찍으면 스크립트를 못 찾은 경우도 정상 완료처럼 보인다)
if ($LASTEXITCODE -eq 99) {
    Write-Host "  (k6 종료코드 99 — 임계값 미달. 측정은 정상 완료)" -ForegroundColor Yellow
}
elseif ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "  k6가 실패했습니다 (종료코드 $LASTEXITCODE). 위 오류를 먼저 해결하세요." -ForegroundColor Red
    Write-Host "  아래 지표는 측정된 것이 아닙니다." -ForegroundColor Red
}

# 결과 파일을 UTF-8로 다시 저장한다.
#
# PS 5.1의 Tee-Object는 인코딩 옵션이 없고 UTF-16LE로 쓴다. 그러면 grep·git diff·
# 웬만한 에디터에서 바이너리처럼 보이고, 아래에서 -Encoding utf8로 요약을 덧붙이면
# 한 파일에 두 인코딩이 섞인다. 여기서 한 번 맞춰 두고 이후 append도 UTF-8로 한다
# BOM을 붙인다($true) — PS 5.1의 Get-Content는 BOM이 없으면 UTF-8을 ANSI로 읽어
# 한글이 깨진다(`최대 VU`가 `理쒕?`로 보인다). grep·git은 BOM이 있어도 문제없다
$raw = Get-Content -Raw $outFile
[System.IO.File]::WriteAllText($outFile, $raw, (New-Object System.Text.UTF8Encoding($true)))

$endedAt = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

# --- 서버 내부 지표 요약 ---------------------------------------------------
Start-Sleep -Seconds 6   # 마지막 scrape 반영 대기
$win = "$($endedAt - $startedAt + 30)s"

function PromQ($expr) {
    try {
        $r = Invoke-RestMethod ("http://localhost:9090/api/v1/query?query=" + [uri]::EscapeDataString($expr)) -TimeoutSec 10
        return $r.data.result[0].value[1]
    }
    catch { return '(없음)' }
}

Write-Host "`n=== 서버 내부 지표 (테스트 구간 최대) ===" -ForegroundColor Cyan
$metrics = [ordered]@{
    '최대 VU'           = "max_over_time(k6_vus[$win])"
    'RPS 최대'          = "max_over_time(sum(rate(http_server_requests_seconds_count{job=`"catcheat-backend`"}[30s]))[${win}:5s])"
    # uri 필터를 걸지 않는다 — 도메인마다 경로가 다르다.
    # 예전에는 `uri="/api/v1/challenges"`가 박혀 있어서 다른 도메인에서는 이 줄이 (없음)으로 나왔다.
    # 테스트가 한 엔드포인트만 때리므로 전체 p95로 충분하다
    '서버 p95 최대(ms)' = "max_over_time(histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job=`"catcheat-backend`"}[1m])))[${win}:5s])*1000"
    '커넥션 사용 최대'  = "max_over_time(hikaricp_connections_active[$win])"
    '커넥션 대기 최대'  = "max_over_time(hikaricp_connections_pending[$win])"
    '획득 대기(ms)'     = "max_over_time((rate(hikaricp_connections_acquire_seconds_sum[1m])/rate(hikaricp_connections_acquire_seconds_count[1m])*1000)[${win}:5s])"
    '톰캣 busy 최대'    = "max_over_time(tomcat_threads_busy_threads[$win])"
    'CPU 최대(%)'       = "max_over_time(process_cpu_usage[$win])*100"
}
$summary = @()
foreach ($k in $metrics.Keys) {
    $v = PromQ $metrics[$k]
    if ($v -ne '(없음)') { $v = [math]::Round([double]$v, 2) }
    Write-Host ("  {0,-18} {1}" -f $k, $v)
    $summary += "$k = $v"
}
$summary | Add-Content -Path $outFile -Encoding utf8

# --- Grafana 링크 ----------------------------------------------------------
# k6 종료 후에도 remote write 시리즈는 stale 표시가 없어 마지막 값이 약 5분 유지된다.
# 그래서 t1을 k6_vus에서 찾지 않고 실제 실행 시각을 쓴다
$from = ($startedAt - 30) * 1000
$to = ($endedAt + 30) * 1000
Write-Host "`n=== GRAFANA (캡쳐용 · 구간 자동 지정) ===" -ForegroundColor Cyan
Write-Host "  http://localhost:3001/d/catcheat-loadtest/catcheat?from=$from&to=$to`n"
