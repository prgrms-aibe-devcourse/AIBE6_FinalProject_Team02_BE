# 부하테스트 — 시작하기

내가 만든 API가 **사용자가 몰리거나 데이터가 쌓였을 때** 어떻게 되는지 재보는 폴더다.

개발할 때 우리 서버는 항상 빠르다. 혼자 쓰고, DB가 비어 있기 때문이다. 이 폴더는 그 두 조건을 미리 만들어 본다.

- **k6** 부하를 만들고 응답을 잰다 (밖에서 본 것)
- **Prometheus** 서버 내부 지표를 모은다 (안에서 본 것)
- **Grafana** 그걸 그린다 (`localhost:3001`)

> 왜 셋 다 필요한가 — 챌린짓 랭킹을 재보니 **k6 임계값은 전부 통과했는데 DB 커넥션풀은 이미 꽉 차 있었다.** k6만 봤다면 "문제없음"이 결론이었다.

---

## 1. 처음 한 번 (준비)

**k6 설치**

| OS | 명령 |
| --- | --- |
| Windows | `winget install --id GrafanaLabs.k6 -e` |
| macOS | `brew install k6` |
| Linux | https://k6.io/docs/get-started/installation |

설치 후 **새 터미널**을 열어야 PATH가 잡힌다.

**컨테이너 두 벌.** 평소 개발용(postgres·redis)과 관측용을 파일로 분리해 뒀다.

```bash
docker compose up -d
```

```bash
docker compose -f monitoring/docker-compose.monitoring.yml up -d
```

**백엔드는 `TEST_LOGIN_ENABLED=true`로 띄운다.** k6가 이 엔드포인트로 로그인 쿠키를 받는다. 없으면 404다.

```powershell
$env:TEST_LOGIN_ENABLED='true'; ./gradlew bootRun
```

```bash
TEST_LOGIN_ENABLED=true ./gradlew bootRun
```

---

## 2. 돌려보기

실행기가 두 벌 있다. **같은 인자, 같은 출력**이니 자기 환경에 맞는 것을 쓰면 된다.

**PowerShell**

```powershell
.\load-test\run.ps1 -Stage smoke
```

**macOS · Linux · Git Bash**

```bash
bash load-test/run.sh --stage smoke
```

| | PowerShell | bash |
| --- | --- | --- |
| 도메인 | `-Domain my-feature` | `--domain my-feature` |
| 단계 | `-Stage load` | `--stage load` |
| VU | `-Vus 150` | `--vus 150` |
| 꼬리표 | `-Tag before` | `--tag before` |
| 대상 서버 | `-BaseUrl https://…` | `--base-url https://…` |
| 도움말 | — | `--help` |

> 맥에서 `./load-test/run.sh`로 쓰고 싶으면 실행 권한을 커밋해 두면 된다 — `git update-index --chmod=+x load-test/run.sh`

실행기가 먼저 사전 점검을 하고, 하나라도 실패하면 멈춘다. 전부 `OK`면 알아서 k6를 돌린다.

```
=== PREFLIGHT ===
  [OK  ] backend :8080          HTTP 302
  [OK  ] metrics :9091          HTTP 200
  [OK  ] prometheus :9090       HTTP 200
  [OK  ] scrape target          up=1
  [INFO] 데이터량                = 5000
```

**3단으로 올린다. 순서를 건너뛰지 않는다.**

| 단계 | 명령 | 시간 | 무엇을 아는가 |
| --- | --- | --- | --- |
| 1 | `-Stage smoke` | 30초 | 스크립트·인증·경로가 맞는지. **checks 100%가 아니면 여기서 멈춘다** |
| 2 | `-Stage baseline` | 1분 | 부하 없는 순수 처리시간. 임계값의 출발점 |
| 3 | `-Stage load -Vus 150 -Tag before` | 5분 30초 | 무릎점과 병목 |

스모크를 건너뛰면 안 되는 이유 — **부하테스트 실패의 절반은 서버가 아니라 스크립트 버그**(경로 오타·토큰·JSON 파싱)다. 5분을 통째로 날린다.

실행이 끝나면 이렇게 나온다.

- **k6 요약표** — p95 · 에러율 · checks
- **서버 내부 지표 요약** — 커넥션 사용/대기 최대, 획득 대기, 톰캣 busy, CPU 최대
- **Grafana 링크** — 시간 범위가 이미 맞춰진 URL (그냥 `localhost:3001`을 열면 빈 화면이다)
- 결과 텍스트는 `results/`에 저장 (git에는 안 올라감)

### 인자

| 인자 | 기본 | 뜻 |
| --- | --- | --- |
| `-Domain` | `challenge-ranking` | 잴 대상 = 이 폴더 하위의 디렉터리 이름 |
| `-Stage` | `load` | `smoke` / `baseline` / `load` |
| `-Vus` | 150 | 최대 VU (가상 사용자 수) |
| `-Tag` | `before` | 결과 파일 이름에 붙는 꼬리표. 개선 후에는 `after` |
| `-BaseUrl` | `http://localhost:8080` | 대상 서버 |

---

## 3. 결과 읽기

### k6 요약표는 위에서 아래로

| 순서 | 항목 | 판단 |
| --- | --- | --- |
| 1 | THRESHOLDS ✓/✗ | ✗면 k6가 exit 99로 끝난다 (실패가 아니라 측정 결과다) |
| 2 | `http_req_failed` | **0%가 아니면 성능 이전에 기능 문제** |
| 3 | `checks` | 200인데 내용이 틀린 경우를 잡는다 |
| 4 | `http_req_duration` **p(95)** | 성능 숫자. 평균이 아니라 이걸 본다 |
| 5 | `http_reqs`의 `/s` | 처리량(RPS) |

`http_req_blocked`가 크면 **내 컴퓨터가 한계**다. VU를 더 올려도 의미 없다.

### 느려졌으면 Grafana에서 원인을 가른다

```
응답시간이 나빠졌다
├─ 1. 에러율          5xx 있음 → 기능 문제 먼저 / 4xx 많음 → 스크립트 버그
├─ 2. RPS가 오르나    안 오르는데 응답시간만↑ → 포화(그 지점이 실제 용량)
├─ 3. CPU             80%↑ → CPU 병목 / 30%↓인데 느림 → 기다리는 중
├─ 4. DB 커넥션풀     대기 > 0 → 커넥션풀 병목
├─ 5. GC              힙 우상향·할당량 급증 → 메모리
└─ 6. 톰캣 스레드     busy가 max에 붙음 → 대개 4·5의 결과
```

**"CPU는 노는데 느리다"가 나오면 거의 항상 기다림(I/O·락·풀 고갈)이다.** 이때 서버 사양을 올리면 돈만 쓴다.

### ⚠️ RPS를 개선 지표로 쓰지 말 것

부하 스크립트에는 think time(사용자가 화면을 보는 시간) 1~3초가 있다. 그래서 VU 150의 이론 상한은 `150 ÷ 2초 ≈ 75 RPS`다. **서버가 10배 빨라져도 이 숫자는 안 오른다.**

실제로 챌린짓 랭킹은 개선 후 RPS가 54 → 57로 거의 그대로였는데, 같은 부하를 **응답시간 1/14, CPU 1/23**로 처리한 것이었다. 서버 한계를 알고 싶으면 VU를 더 올리거나 개방형(`ramping-arrival-rate`)으로 바꿔야 한다.

---

## 4. 내 기능에 적용하기

```powershell
Copy-Item -Recurse load-test\_template load-test\my-feature
```

```bash
cp -r load-test/_template load-test/my-feature
```

`my-feature/target.js`의 **TODO 4곳**만 채우면 된다. 01·02·03 스크립트는 이 파일만 보므로 대개 손댈 필요가 없다.

| TODO | 내용 |
| --- | --- |
| 1 | `NAME` — 화면 이름 (로그·결과 파일에 찍힌다) |
| 2 | `DATA_NOTE` — **이 API의 성능을 지배하는 변수가 무엇인가** |
| 3 | `url()` — 때릴 주소 |
| 4 | `THRESHOLDS` — baseline 실측 후에 채운다 |

```powershell
.\load-test\run.ps1 -Domain my-feature -Stage smoke
```

```bash
bash load-test/run.sh --domain my-feature --stage smoke
```

### TODO 2가 가장 중요하다

**부하(VU)만 변수가 아니다.**

| API 성격 | 지배 변수 | 준비할 것 |
| --- | --- | --- |
| 목록 조회 | 행 수 N | **시딩**. 데이터가 없으면 아무것도 증명하지 못한다 |
| 이미지 업로드 | 파일 크기 | 여러 크기의 샘플 |
| 동시 쓰기 | 같은 행을 노리는 경쟁 정도 | 같은 대상을 노리는 VU 구성 |
| 외부 API 연동 | 그쪽 응답시간 | — |

챌린짓 랭킹이 그랬다. **VU보다 진행중 챌린지 개수 N이 성능을 지배**해서, 데이터 3건인 DB에 VU 200을 때려도 "빠르다"만 나왔다. 여기를 잘못 짚으면 5분을 돌려도 아무것도 알 수 없다.

데이터량이 변수라면 두 개를 더 둔다.

- `seed/` — 시드·정리 SQL (**로컬 전용**)
- `data-count.sql` — 그 변수를 세는 쿼리 **한 줄, 숫자 하나**. 그러면 `run.ps1`이 실행할 때마다 데이터량을 찍어 준다

**시딩 후에는 반드시 개수를 다시 센다.** 시드가 롤백되면 `INSERT 0 4500`은 찍히는데 데이터는 그대로다.

### 임계값 정하는 법

1. **먼저 잰다** — `-Stage baseline` (VU 1). 부하 없는 최선값
2. **계산한다** — `p(95) = baseline × 2~3배`, `p(99) = p95 × 2배`
3. **상한을 씌운다** — 계산값이 1초를 넘으면 임계값이 아니라 서버를 고쳐야 한다는 신호

임계값의 목적은 "지금 통과"가 아니라 **"나중에 나빠진 것을 잡기"**다. 지금 14ms인데 임계값을 3초로 두면 800ms로 나빠져도 초록불이다. 그리고 데이터량에 따라 같은 코드가 8ms도 358ms도 되므로 **"N=몇일 때"를 주석에 함께 남긴다.**

---

## 5. 폴더 규약

```
load-test/
├── lib/                        공통 — 도메인과 무관. 손댈 일이 거의 없다
│   ├── config.js               서버 주소 · 인증 쿠키 이름 · 기본 임계값 · 부하 단계
│   └── auth.js                 login() · useToken()
├── challenge-ranking/          도메인 하나 = 폴더 하나
│   ├── target.js               ★ 무엇을 때릴지. 도메인 고유한 것은 전부 여기
│   ├── 01-smoke.js             30초 · 스크립트가 맞는지
│   ├── 02-baseline.js          1분 VU1 · 기준선
│   ├── 03-load.js              5분 · 부하 곡선
│   ├── data-count.sql          (선택) 성능을 지배하는 변수를 세는 쿼리
│   └── seed/                   (선택) 시드·정리 SQL
├── _template/                  새 도메인 시작점 — 복사해서 쓴다
├── run.ps1                     실행기 (PowerShell)
├── run.sh                      실행기 (bash) — 둘은 같은 일을 한다
└── results/                    실행 결과 (gitignore)
```

**실행기는 도메인을 모른다.** `load-test/<도메인>/`에서 `01-smoke.js` · `02-baseline.js` · `03-load.js`를 찾아 쓸 뿐이다. 이름만 맞추면 새 도메인이 그냥 굴러간다. `data-count.sql`과 `seed/`는 있으면 쓰고 없으면 건너뛴다.

`run.ps1`과 `run.sh`는 같은 동작을 하도록 맞춰 뒀다. **한쪽을 고치면 다른 쪽도 고쳐야 한다** — 컴파일러가 잡아 주지 않는다.

공통(`lib/`)과 도메인(`target.js`)을 가른 이유 — 처음에는 `lib/config.js`에 챌린지 랭킹의 URL과 정렬 종류가 섞여 있었다. 그러면 다른 기능을 재려는 사람이 자기와 무관한 코드를 읽고 고쳐야 한다.

---

## 6. 주의

- **시드 SQL은 로컬 전용이다.** 운영 DB에서 절대 실행하지 않는다
- **배포 서버(`-BaseUrl` / `--base-url`)로 쏘기 전에 팀에 공지한다.** 시연 일정과 겹치면 안 되고, EC2가 t계열이면 CPU 크레딧이 소진된다. 낮은 VU부터 올린다
- `results/`는 git에 올라가지 않는다. 측정 근거를 남길 거면 수치와 그래프를 문서에 정리한다
- Grafana의 **한눈에** 구역 stat 패널은 「화면 시간 범위의 최대값」이다. 마지막 값이 아니다
- 절대 수치는 배포 성능이 아니다. `bootRun`은 JIT를 C1에서 멈추므로(`-XX:TieredStopAtLevel=1`) **전·후 비교용으로 읽는다**

## 7. 막혔을 때

| 증상 | 원인 |
| --- | --- |
| 사전 점검에서 `backend :8080` FAIL | 백엔드가 안 떠 있음 |
| 사전 점검에서 `metrics :9091` FAIL | `management.server.port` 설정 또는 Security 예외 누락 |
| 사전 점검에서 `scrape target` up=0 | Prometheus 컨테이너가 앱에 못 닿음 (`host.docker.internal`) |
| smoke에서 401·302 | 백엔드에 `TEST_LOGIN_ENABLED=true`가 없음 |
| 부하를 걸어도 빠름 | **데이터가 없음.** `data-count.sql`이 찍는 숫자를 확인 |
| Grafana가 빈 화면 | 시간 범위가 "최근 15분". 실행기가 준 링크를 쓴다 |
| k6 패널만 빔 | Prometheus에 `--web.enable-remote-write-receiver`가 없음 |
| `k6를 찾을 수 없습니다` | 설치 후 새 터미널을 안 열었음. 실행기가 기본 설치 경로도 찾아보지만 실패할 수 있다 |
| `couldn't be found on local disk` | Windows k6가 POSIX 경로를 못 읽는 경우. `run.sh`가 `cygpath`로 변환하니 최신 스크립트를 쓸 것 |
| k6 종료코드가 0이 아님 | **99만 임계값 미달(정상 측정)이다.** 나머지는 실행 실패이고 실행기가 구분해서 알려 준다 |

**실행기를 고칠 일이 있으면 주석을 먼저 읽을 것.** 플랫폼마다 다르게 동작하는 것들에 대응해 둔 자리가 있다.

| 환경 | 대응해 둔 것 |
| --- | --- |
| Windows PowerShell **5.1** | 302 판정(`Invoke-WebRequest`가 상태코드를 잃는다) · k6 stderr(`2>&1`이 스크립트를 죽인다) · 한글 인코딩(파일 BOM + 콘솔) · 결과 파일 UTF-16 |
| Git Bash (MinGW) | `curl -o /dev/null`이 exit 23로 끝난다 → `NUL` 사용 · Windows k6가 POSIX 경로를 못 읽는다 → `cygpath -w` |

PowerShell 7이나 맥에서는 위 대부분이 나타나지 않는다. **환경이 다르면 도구부터 막힌다**는 걸 여기서 배웠다.
