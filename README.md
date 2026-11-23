# E!ffect RMA Backend

PCB 반품(RMA)을 접수하고, 품질 판정과 원인분석 결과를 조회하는 Spring 백엔드입니다. 이 브랜치는 **Spring API v6**와 **PCB RMA 데이터 v5**를 기준으로 합니다. Vue는 Spring의 `/api`를 호출합니다. 로컬에서는 Spring이 목업 AI를 사용합니다.

## 실행 환경

- Java 25와 저장소에 포함된 Gradle Wrapper
- Docker 및 Docker Compose
- PCB RMA v5 전체 SQLite 원본 `pcb_rma.sqlite`
- 원본을 적재할 때 Python 3, PostgreSQL 클라이언트 `psql`, [effect-ai의 PostgreSQL 적재 도구](https://github.com/E-ffects/effect-ai/tree/def68f47febcbba9a16de7dfb0c9887e13c67da5/db/postgres)

## 로컬에서 실행

저장소 루트에서 PostgreSQL과 Redis를 시작하고 로컬 설정을 만듭니다.

```sh
docker compose up -d
cp src/main/resources/application-local.example.yml src/main/resources/application-local.yml
```

팀에서 공유한 PCB v5 원본을 **빈 로컬 DB에 최초 한 번** 적재합니다. 아래에서 `EFFECT_AI_DIR`, `PCB_V5_SQLITE`는 각 로컬 경로를 가리킵니다. 이미 v5가 적재된 DB라면 건수를 확인하고 바로 Spring을 실행하면 됩니다.

```sh
EFFECT_AI_DIR=/path/to/effect-ai
PCB_V5_SQLITE=/path/to/pcb_rma.sqlite
PCB_PG_PACKAGE="$(mktemp -d)"

python3 "$EFFECT_AI_DIR/db/postgres/build.py" "$PCB_V5_SQLITE" "$PCB_PG_PACKAGE" --schema pcb
(
  cd "$PCB_PG_PACKAGE"
  psql 'postgresql://effect@localhost:5433/effect' -v ON_ERROR_STOP=1 -f load.sql
)
psql 'postgresql://effect@localhost:5433/effect' -c 'SELECT COUNT(*) FROM pcb.rma_claim;'
```

v5 원본 건수는 **10,610건**입니다. `pcb`에는 원본과 접수·품목·QA 판정이 들어가고, Flyway가 관리하는 `app`에는 접수 상태·첨부 메타데이터·분석 및 판정 이력이 들어갑니다. 도구가 생성한 적재 스크립트는 빈 스키마 기준입니다.

```sh
SPRING_PROFILES_ACTIVE=local AI_MODE=mock ./gradlew bootRun
```

- API 기준 주소는 `http://localhost:8000/api`입니다.
- Swagger UI는 `http://localhost:8000/api/swagger-ui/index.html`입니다.
- 로컬 첨부는 `./storage/attachments/{rma_id}/`에 저장합니다.
- 로컬 업무 시각은 `app.clock.fixed` 기준인 `2025-12-31T09:00:00+09:00`입니다. 분석 조회 간격은 실제 경과 시간으로 계산합니다.

## 구현된 API

| 기능 | 경로 |
| --- | --- |
| 인증 | `/api/auth/signup`, `/login`, `/reissue`, `/logout`, `/check-username`, `/me` (모두 `/api/auth` 아래) |
| 접수 폼·상세 | `GET /api/meta`, `GET /api/rmas/{rmaId}` |
| RMA 사례 목록·CSV 다운로드 | `GET /api/rmas`, `GET /api/rmas/export` |
| CS 대시보드 KPI | `GET /api/dashboard/cs` |
| 접수·재처리 | `POST /api/intake`, `POST /api/rmas/{rmaId}/intake/retry` |
| QA 판정 | `GET /api/triage-reviews`, `PUT /api/rmas/{rmaId}/triage` |
| 분석·승인 목록 | `GET/POST /api/rmas/{rmaId}/analysis`, `GET /api/approvals` |
| 과거 유사 사례 | `GET /api/rmas/{rmaId}/similar` |
| 원인 판정 | `POST /api/rmas/{rmaId}/cause-decision` |
| 고객 회신·처리 | `GET/POST /api/rmas/{rmaId}/communications`, `POST /api/rmas/{rmaId}/service-action` |
| 첨부 | `GET /api/rmas/{rmaId}/attachments`, `GET /api/attachments/{attachmentId}` |
| AI 상태 | `GET /api/ai/health` |

인증이 필요한 요청은 `Authorization: Bearer <access_token>`을 보냅니다. 일반 응답은 `{code, message, content}` 형식이고, 첨부 다운로드는 원본 파일 본문을 반환합니다. 승인 목록은 기본 L3 대기 목록이며 `level=L1_L2`와 `status=WAITING` 또는 `CONFIRMED`를 조합할 수 있습니다.

### RMA 사례 목록

- `content`의 `items`, `total`, `page`, `size`를 사용합니다. 기본값은 접수일 최신순, 1페이지, 30건이며 한 페이지에 최대 100건을 조회합니다. 한 접수에 품목이 여럿이면 `claimed_defect_qty`는 품목별 신고 수량의 합계입니다.
- 목록 값은 `opened_at`(+09:00), `customer_name`, `reported_part_no`, `detection_stage`, `major_name`, `minor_name`, `classification_status`, `claimed_defect_qty`, `analysis_level`, `cs_status`, `analysis_state` 등을 사용합니다. `severity`, `qa_status`, `approval_status`, `recurrence_verdict`는 QUALITY에 제공하며, 값이 없는 항목은 생략합니다.
- `q`는 RMA 번호·고객사·제품 번호·불량 분류 이름을 검색합니다. `product`는 `part_no`, `defectType`은 `major_name`, `classification`은 `classification_status`, `status`는 `cs_status` 값입니다. `from`·`to`는 서울 기준 접수일(양끝 포함), `overDays`는 미종료 접수의 최소 경과일입니다. QUALITY는 `severity`와 `recurrence`도 필터로 사용할 수 있습니다.
- `sort=opened_at:desc,reported_part_no:asc`처럼 여러 열을 정렬할 수 있습니다. `open=RMA-08927`을 주면 검색 조건에 포함된 해당 건의 페이지를 반환합니다. `open_found`는 검색 결과에 포함됐는지, `open_exists`는 접수 자체가 존재하는지를 나타냅니다.

### RMA CSV 다운로드

- `GET /api/rmas/export?status=OPEN&sort=opened_at:desc`는 목록과 같은 필터와 정렬을 적용하고, `page`, `size`, `open`과 관계없이 검색 결과 전체를 내려줍니다.
- 성공 응답은 `text/csv; charset=UTF-8` 파일 본문과 `Content-Disposition: attachment; filename="rma.csv"`를 반환합니다. 한글 헤더와 Excel 호환용 UTF-8 BOM을 포함하며 결과가 0건이면 헤더만 반환합니다.
- CS에는 품질 전용 열이 포함되지 않습니다. 원문·첨부·원인 후보는 CSV 대상에 포함되지 않습니다.

### CS 대시보드 KPI

- CS 계정으로 `GET /api/dashboard/cs?from=2025-12-02&to=2025-12-31`을 호출합니다. 날짜는 서울 기준이며 시작일과 종료일을 모두 포함합니다. 종료일을 생략하면 앱 기준일을, 시작일을 생략하면 종료일을 포함한 30일 범위를 사용합니다. 잘못된 기간은 400 `INVALID_INPUT`을 반환합니다.
- `content.period`에 적용된 `from`·`to`를, `content.as_of`에 조회 기준 시각을 반환합니다. `content.kpis`에는 다음 값을 반환합니다.

| 필드 | 계산 기준 |
| --- | --- |
| `received_count` | 선택 기간의 `pcb.rma_claim.opened_at` 접수 건수 |
| `pending_count` | 선택 기간에 상관없이 현재 `cs_status`가 `CLOSED`가 아닌 전체 건수 |
| `closed_count` | `CLOSED` 접수마다 품목들의 가장 늦은 `pcb.service_action.executed_at`을 종료일로 사용해 선택 기간의 건수를 계산 |
| `estimated_loss_krw` | 선택 기간의 `pcb.loss_ledger.occurred_at`에 해당하는 `amount_krw` 합계 |

기존 목업 대시보드 화면과 연결할 때는 위 네 필드를 사용합니다. 제품별 위젯·고객 만족도·AI 요약은 별도 범위입니다.

### 과거 유사 사례

- `GET /api/rmas/{rmaId}/similar?limit=5`에서 `content`의 `rma_id`, `as_of`, `items`를 읽습니다. 기본 5건이며 1~20건을 지정할 수 있습니다. 분석 기준 시각과 결과 순서는 FastAPI의 계산을 사용합니다.
- CS와 QUALITY는 과거 접수 번호·일시, 분류, 유사도, 해결 소요 일수를 받습니다. 비교 근거, 과거 조치, 재발, 원인·공정·설비, 원문 일부, 대책은 QUALITY에 제공됩니다.
- 조회 결과가 없으면 `items: []`입니다. 접수 없음은 404 `RMA_NOT_FOUND`, 입력이 잘못됐거나 QA 판정 전이라 조회 기준 시각을 정할 수 없으면 400 `INVALID_INPUT`, FastAPI 장애 또는 연동 미설정은 503 `AI_UNAVAILABLE`입니다.

## 로컬에서 흐름 확인

1. CS 토큰으로 접수 폼과 `raw_file`을 `POST /api/intake`에 보내면 접수 번호와 분석 등록 상태가 반환됩니다. 같은 사용자가 같은 `Idempotency-Key`로 다시 보내면 기존 접수 번호를 반환합니다.
2. 원문에 `정보 부족`을 넣으면 보충 질문을 반환합니다. `임시 판정`을 넣으면 QUALITY가 임시 QA 목록에서 확인한 다음 분석이 자동 시작됩니다.
3. `GET /api/rmas/{rmaId}/analysis`에서 진행 상태와 최종 결과를 확인합니다. QUALITY는 전체 결과를, CS는 회신 초안과 처리방안이 포함된 화면용 결과를 받습니다. 원문에 `처리 기록 없음`을 넣으면 처리방안은 `null`입니다.

4. QUALITY가 완료 분석에 원인 판정을 저장합니다. 승인·확인하면 조사 및 채택 원인을 연결하고 목업 AI 사례 갱신을 예약합니다.
5. CS가 회신을 기록하면 `RESPONDED`, 처리 실행 결과를 저장하면 `CLOSED`가 됩니다. L3 접수는 원인 판정 이후에 처리 실행을 기록할 수 있습니다.

Spring API v6의 MVP 경로는 로컬에서 기본 목업으로 실행할 수 있습니다. 실제 FastAPI 연결과 운영 프로필 설정은 아래를 참고합니다.

## 실제 FastAPI 연결

1. `effect-ai` 환경에서 `RMA_DATABASE_URL`과 `RMA_DB_SCHEMA=pcb`를 설정합니다. Spring이 쓰는 PostgreSQL과 같은 DB를 사용하고, FastAPI에는 `pcb` 읽기 전용 계정을 사용합니다.
2. FastAPI의 `RMA_SERVICE_JWT_SECRET`을 Spring의 `JWT_SECRET`과 같은 값으로 설정합니다. `effect-ai` 저장소 루트에서 `python -m uvicorn server.app:create_app --factory --host 127.0.0.1 --port 8080`으로 시작합니다.
3. Spring은 `SPRING_PROFILES_ACTIVE=local AI_MODE=http AI_BASE_URL=http://127.0.0.1:8080/ai/v1 ./gradlew bootRun`으로 시작합니다. 기본 `AI_MODE=mock`을 사용하던 자리에 `http`를 설정하면 기존 화면 API가 실제 FastAPI를 호출합니다.
4. Vue는 Spring의 `GET /api/ai/health`를 사용합니다. `content.available`은 요청 처리 가능 여부이며 `llm=off`인 규칙 기반 모드에서도 DB가 정상이면 `true`입니다. FastAPI는 모델 이름을 제공하지 않으므로 `llm=ok`일 때는 화면에 특정 모델명을 표시할 근거가 없습니다. 연결 장애는 `available=false`로 표시합니다.

## 운영 프로필과 첨부 저장

| 환경 변수 | 설정 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | v5 `pcb`와 Flyway 관리 `app`이 들어 있는 PostgreSQL |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` | 인증 세션용 Redis |
| `JWT_SECRET` | Spring과 FastAPI가 공유하는 시크릿. FastAPI에는 `RMA_SERVICE_JWT_SECRET`으로 주입 |
| `AI_BASE_URL` | 내부 FastAPI 서비스의 `/ai/v1` 주소. `prod`의 기본 `AI_MODE`는 `http` |
| `APP_ATTACHMENTS_S3_BUCKET`, `AWS_REGION` | 운영 첨부 버킷과 리전 |
| `APP_ATTACHMENTS_S3_ENDPOINT` | S3 호환 로컬 검증 환경에서만 지정하는 엔드포인트 |

- 운영 첨부는 비공개 버킷의 `attachments/{rma_id}/{uuid}`에 저장하고, 다운로드 요청은 Spring에서 권한 확인 후 스트리밍합니다. 실행 역할에는 해당 객체 경로의 `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` 권한이 필요합니다. S3는 호출자에게 `s3:ListBucket` 권한이 있을 때 없는 객체를 404로 응답하므로 버킷 권한도 확인합니다. [S3 GetObject 권한](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html)
- Spring DB 트랜잭션 롤백 시 이번 요청의 객체를 삭제합니다. 기존 로컬 첨부가 들어 있는 DB를 운영에 그대로 쓸 경우, 파일을 같은 저장 키의 `attachments/` 아래에 옮긴 뒤 다운로드를 확인합니다.
- Spring이 `pcb`에 쓰는 문자열 시각은 FastAPI와 동일하게 서울 기준 초 단위입니다. V10은 기존 Spring 접수·분류·QA의 관련 시각을 맞추며 `app`의 정밀 저장 시각은 유지합니다.
- 스테이징에서는 가입·역할 부여 정책을 먼저 확정하고, CS 원문 접수 → QUALITY 판정·분석 → 원인 판정 → CS 회신·종료와 재시작 후 접수·분석·첨부 보존을 확인합니다.

## 테스트와 기여

### 스테이징 배포 후 자동 점검

Python 3으로 [스테이징 점검 스크립트](scripts/staging_smoke.py)를 실행합니다. 팀이 만든 CS·QUALITY 테스트 계정의 값을 `STAGING_CS_USERNAME`, `STAGING_CS_PASSWORD`, `STAGING_QUALITY_USERNAME`, `STAGING_QUALITY_PASSWORD` 환경 변수로 전달합니다. 스크립트는 Spring API에 접속하며 AWS 자격 증명을 사용하지 않습니다.

```sh
python3 scripts/staging_smoke.py --base-url https://staging.example.com
# 승인된 스테이징에서만 테스트 접수 한 건을 생성·종료
python3 scripts/staging_smoke.py --base-url https://staging.example.com --e2e
# Spring 재시작 뒤 앞 단계에서 출력된 테스트 번호·해시로 다시 조회
python3 scripts/staging_smoke.py --base-url https://staging.example.com \
  --verify-rma "$STAGING_SMOKE_RMA_ID" --attachment-sha256 "$STAGING_SMOKE_SHA256"
```

기본 모드는 로그인·역할, 실제 FastAPI 상태, v5 고객사·제품과 샘플 케이스 조회를 검사합니다. `--e2e`는 합성 원문으로 테스트 RMA 한 건을 생성하고, 중복 접수 방지·첨부 바이트 일치·QA·분석·원인 판정·CS 회신 및 종료를 확인합니다. E2E 실행은 점검용 접수와 이력을 남깁니다. 기본 샘플은 v5 합성 접수 `RMA-08927`이고, 스테이징 데이터가 다르면 같은 종류의 샘플을 `--sample-rma`로 지정합니다. 각 검사 결과와 실패한 단계가 터미널에 표시되며 실패 시 종료 코드는 1입니다.

```sh
./gradlew test
```

Docker가 실행 중이어야 PostgreSQL·Redis 통합 테스트가 동작합니다. 테스트 데이터와 첨부는 임시 환경을 사용합니다.

작업 브랜치명, 한국어 커밋 제목, PR과 리뷰는 [백엔드 팀 규칙](docs/CONVENTION.md)을 따릅니다. `develop`에는 PR을 통해 변경을 합칩니다.
