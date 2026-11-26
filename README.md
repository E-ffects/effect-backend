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

공용 DB의 원본 접수·계정 권한과 재적재 전후의 신규 접수 보존은 [PostgreSQL v5 점검 절차](docs/POSTGRES_V5.md)를 사용합니다.

```sh
SPRING_PROFILES_ACTIVE=local AI_MODE=mock ./gradlew bootRun
```

- API 기준 주소는 `http://localhost:8000/api`입니다.
- Swagger UI는 `http://localhost:8000/api/swagger-ui/index.html`입니다.
- 로컬 첨부는 `./storage/attachments/{rma_id}/`에 저장합니다.
- 로컬 업무 시각은 `app.clock.fixed` 기준인 `2025-12-31T09:00:00+09:00`입니다. 분석 조회 간격은 실제 경과 시간으로 계산합니다.

## 관련 코드 위치

- RMA의 접수, 분석, QA·원인 검토, 고객 회신·처리는 각 계층의 `intake`, `analysis`, `review`, `communication` 패키지에서 찾을 수 있습니다. 공통 RMA 목록·상세·첨부는 `rma`의 각 계층에 있습니다.
- `rma/application/intake/IntakeWorkflowService`는 접수 후 처리 순서를, `IntakeWorkflowStateService`는 상태 저장을, `IntakeResponseQuery`는 같은 응답 형식으로 상태를 조회하는 일을 담당합니다.
- `investigation`은 품질 대시보드와 불량 패턴이 공유하는 접수 집계와 조사 순위를 담당합니다. 각 화면은 조회 결과를 자체 응답 형식으로 변환합니다.
- `recurrence`, `report`, `patternreport`, `alert`는 조사 대책·재발, 단건 RMA 보고서, 불량 패턴 보고서, 사용자별 알림 상태를 담당합니다. 서울 날짜 범위와 페이지 검증은 `global`에 있습니다.

## 구현된 API

| 기능 | 경로 |
| --- | --- |
| 인증 | `/api/auth/signup`, `/login`, `/reissue`, `/logout`, `/check-username`, `/me` (모두 `/api/auth` 아래) |
| 대시보드 현황 요약 | `GET /api/dashboard/cs/summary`, `GET /api/dashboard/quality/summary` |
| 접수 폼·상세 | `GET /api/meta`, `GET /api/rmas/{rmaId}` |
| RMA 사례 목록·CSV 다운로드 | `GET /api/rmas`, `GET /api/rmas/export` |
| CS 대시보드 KPI | `GET /api/dashboard/cs` |
| 품질 대시보드 KPI·불량 현황·조사 우선순위 | `GET /api/dashboard/quality` |
| 품질 위젯 배치·주간 접수 추이 | `GET/PUT /api/dashboard/layout/quality`, `GET /api/dashboard/trend` |
| 반복 불량 패턴·조사 근거 | `GET /api/patterns`, `GET /api/patterns/{patternId}` |
| 조사별 대책·효과·재발 판정 이력 | `GET /api/recurrence/actions`, `GET /api/recurrence/actions/{actionId}/trend`, `GET /api/recurrence/reviews` |
| 품질 알림과 개인 상태 | `GET /api/alerts`, `POST /api/alerts/{id}/read`, `POST /api/alerts/{id}/star`, `POST /api/alerts/read-all`, `POST /api/alerts/bulk-read`, `POST /api/alerts/bulk-delete`, `DELETE /api/alerts/{id}` |
| RMA 보고서·패턴 요약 | `GET /api/reports`, `GET/POST/PUT /api/reports/{rmaId}`, `POST /api/reports/{rmaId}/draft`, `POST /api/reports/{rmaId}/replace`, `POST/DELETE /api/reports/{rmaId}/review`, `GET /api/dashboard/pattern-report/{patternId}` |
| 불량 패턴 보고서 | `POST /api/patterns/{patternId}/reports`, `GET /api/pattern-reports`, `GET /api/pattern-reports/{reportId}`, `POST /api/pattern-reports/{reportId}/delivery-preview` |
| 접수·재처리 | `POST /api/intake`, `POST /api/rmas/{rmaId}/intake/retry`, `POST /api/rmas/{rmaId}/intake/reply` |
| QA 판정 | `GET /api/triage-reviews`, `PUT /api/rmas/{rmaId}/triage` |
| 분석·승인 목록 | `GET/POST /api/rmas/{rmaId}/analysis`, `GET /api/approvals` |
| 과거 유사 사례 | `GET /api/rmas/{rmaId}/similar` |
| 원인 판정 | `POST /api/rmas/{rmaId}/cause-decision` |
| 고객 회신·처리 | `GET/POST /api/rmas/{rmaId}/communications`, `POST /api/rmas/{rmaId}/service-action` |
| 고객 2차 회신 초안 | `POST /api/rmas/{rmaId}/communications/follow-up-draft` |
| 고객 처리 이력 | `GET /api/rmas/{rmaId}/service-actions` |
| 첨부 | `GET /api/rmas/{rmaId}/attachments`, `GET /api/attachments/{attachmentId}` |
| AI 상태 | `GET /api/ai/health` |

인증이 필요한 요청은 `Authorization: Bearer <access_token>`을 보냅니다. 일반 응답은 `{code, message, content}` 형식이고, 첨부 다운로드는 원본 파일 본문을 반환합니다. 승인 목록은 기본 L3 대기 목록이며 `level=L1_L2`와 `status=WAITING` 또는 `CONFIRMED`를 조합할 수 있습니다.

- RMA 상세에서는 QUALITY가 `TRIAGE_REVIEW` 상태의 `content.triage_proposal`에서 `severity`, `characteristic_symbol`, `analysis_level`, `rationale`, `provisional`을 읽습니다. QA 확인 이후에는 `content.triage`를 사용합니다.
- 승인 목록의 각 항목에 `customer_name`과 `reported_part_no`가 들어갑니다.

### RMA 사례 목록

- `content`의 `items`, `total`, `page`, `size`를 사용합니다. 기본값은 접수일 최신순, 1페이지, 30건이며 한 페이지에 최대 100건을 조회합니다. 한 접수에 품목이 여럿이면 `claimed_defect_qty`는 품목별 신고 수량의 합계입니다.
- 목록 값은 `opened_at`(+09:00), `customer_name`, `reported_part_no`, `detection_stage`, `major_name`, `minor_name`, `classification_status`, `claimed_defect_qty`, `analysis_level`, `cs_status`, `analysis_state` 등을 사용합니다. `severity`, `qa_status`, `approval_status`, `recurrence_verdict`는 QUALITY에 제공하며, 값이 없는 항목은 생략합니다.
- `q`는 RMA 번호·고객사·제품 번호·불량 분류 이름을 검색합니다. `product`는 `part_no`, `defectType`은 `major_name`, `classification`은 `classification_status`, `status`는 `cs_status` 값입니다. `from`·`to`는 서울 기준 접수일(양끝 포함), `overDays`는 미종료 접수의 최소 경과일입니다. QUALITY는 `severity`와 `recurrence`도 필터로 사용할 수 있습니다.
- `sort=opened_at:desc,reported_part_no:asc`처럼 여러 열을 정렬할 수 있습니다. `open=RMA-08927`을 주면 검색 조건에 포함된 해당 건의 페이지를 반환합니다. `open_found`는 검색 결과에 포함됐는지, `open_exists`는 접수 자체가 존재하는지를 나타냅니다.

### RMA CSV 다운로드

- `GET /api/rmas/export?status=OPEN&sort=opened_at:desc`는 목록과 같은 필터와 정렬을 적용하고, `page`, `size`, `open`과 관계없이 검색 결과 전체를 내려줍니다.
- 성공 응답은 `text/csv; charset=UTF-8` 파일 본문과 `Content-Disposition: attachment; filename="rma.csv"`를 반환합니다. 한글 헤더와 Excel 호환용 UTF-8 BOM을 포함하며 결과가 0건이면 헤더만 반환합니다.
- CS에는 품질 전용 열이 포함되지 않습니다. 원문·첨부·원인 후보는 CSV 대상에 포함되지 않습니다.

### 고객 처리 이력

- CS와 QUALITY는 `GET /api/rmas/{rmaId}/service-actions?page=1&size=30`에서 접수에 속한 모든 품목의 처리 예약·실행 내역을 조회합니다. 실행 시각 또는 예약의 결정 시각 기준 최신순이며 `content.items`와 `content.total`을 사용합니다.
- 각 항목에는 품목 ID, 처리 유형, 수량, 결과, 결정·실행 시각이 포함됩니다. 예약은 `executed_at: null`이고, 기존 고객 회신은 `/communications`에서 가져옵니다.

### 고객 보충 회신

- CS는 `NEEDS_INFO` 상태에서 `POST /api/rmas/{rmaId}/intake/reply`에 `{"message_text":"고객이 보낸 추가 답변"}`을 보내 회신을 저장하고 분류를 다시 시작할 수 있습니다. 선택 헤더 `Idempotency-Key`로 중복 등록을 막습니다. 분류 장애가 발생하면 기존 `/intake/retry`가 저장된 회신을 다시 사용합니다.
- 접수 상세를 다시 열면 `NEEDS_INFO`의 `follow_up_questions` 또는 `FAILED`의 `error.{code, detail}`을 읽을 수 있습니다. 화면은 현재 접수 상태에 맞춰 질문과 실패 안내를 표시합니다.

### 원인 확정 후 2차 회신 초안

- CS는 원인 승인 또는 확인 후 `POST /api/rmas/{rmaId}/communications/follow-up-draft`를 본문 없이 호출합니다. Spring 기본 문안과 회신 기록용 `analysis_id`, `source: MANUAL`을 반환합니다. 실제 회신을 기록할 때는 기존 `POST /api/rmas/{rmaId}/communications`를 사용합니다.
- CS 접수 상세의 `follow_up_draft_available`로 버튼 표시 여부를 결정합니다. 상세 조회와 초안 요청은 같은 원인 확정 조건을 사용합니다.
- AI 생성은 FastAPI의 2차 회신 전용 경로가 준비되면 연결하는 범위입니다.

### 에이전트 패널

- 패널 연결에는 Spring의 접수 상세·분석·유사 사례 API를 사용합니다. QUALITY의 분석 응답에 판정이 있으면 `cause_decision.approval_status`와 채택 시 `adopted_cause_code`가 들어갑니다. 후보는 `result.cause_candidates[]`에서 코드로 찾습니다.
- 자유 질의는 `POST /api/chat`입니다. 본문은 `{message, scope: {type, id, from?, to?}, thread_id?}`이고 `scope.type`은 `RMA`(상세 화면, QUALITY·CS) 또는 `PATTERN`(불량 패턴 화면, QUALITY)입니다. `from`·`to`는 패턴 집계 기간입니다.
- 대화는 사용자·범위마다 하나가 열려 있습니다. `thread_id` 없이 보내면 그 범위의 이전 대화를 닫고 새 대화를 시작하며, 응답의 `thread_id`를 다음 질문에 보냅니다.
- 응답은 `{thread_id, status, answer, evidence[{type, rma_id, label}], suggestions[{task, label}], created_at}`입니다. `status`는 `ANSWERED`, `NEED_ANALYSIS`, `OUT_OF_SCOPE`, `FALLBACK`이고, 분석이 없는 RMA는 `NEED_ANALYSIS`와 `start_analysis` 제안을 받습니다.
- AI에 보내는 문맥은 화면과 같은 조회 서비스(`RmaDetailService`, `RmaAnalysisService`, `PatternService`)를 거쳐 CS에는 원인·설비·Lot 정보가 들어가지 않습니다. 대화는 Spring이 `app.chat_thread`·`app.chat_message`에 저장하고 최근 10개를 AI에 함께 보냅니다(AI 서버는 상태 없음).
- 오류: 다른 사용자·다른 범위·닫힌 대화 404 `CHAT_THREAD_NOT_FOUND`, 사용자당 1분 10회 초과 429 `CHAT_RATE_LIMITED`(Redis, 장애 시 통과), AI 장애 503 `AI_UNAVAILABLE`(저장하지 않음). 마지막 메시지 후 30일이 지난 대화는 매일 04:30에 삭제합니다.

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

### 품질 대시보드

- QUALITY 계정으로 `GET /api/dashboard/quality?from=2025-12-02&to=2025-12-31`을 호출합니다. 기간 규칙은 CS 대시보드와 같고, 비교 기준은 선택 기간 바로 앞의 같은 길이입니다.
- `content.kpis`는 총 RMA, 반복 조사, 재발 의심, 추정 손실, 원인 확정률과 대책 효과율을 반환합니다. 추정 손실은 원장 발생일 기준입니다. 확정률·효과율에는 분자 `num`과 분모 `den`이 들어갑니다. 분모나 직전 값이 0이면 해당 비율 또는 증감률은 `null`입니다.
- `defect_top5`의 `defect_type`은 목록의 `defectType` 필터에 바로 쓸 수 있는 불량 대분류입니다. `repeat_rank`는 기간 내 접수 2건 이상이 연결된 조사 목록입니다. `priority`는 조사별 가중 점수 상위 5건이며 손실 점수는 `pcb.loss_by_investigation.total_krw`로 계산합니다.
- `repeat_rank`와 `priority` 항목은 `investigation_id`, 같은 값인 `pattern_id`, 대표 `rma_id`를 반환합니다. `pattern_id`로 불량 패턴 상세를, `rma_id`로 개별 케이스를 열 수 있습니다. `priority.evidence`에는 최신 근거 RMA가 최대 5건 들어갑니다.
- 위젯 배치는 QUALITY 계정별로 저장합니다. 조회 시 `content.layout`과 `content.default`를 받습니다. 저장 시 위젯 네 개의 `id`, `width`, `hidden`을 배열로 보내며 배열 순서가 표시 순서입니다.
- 추이 API는 `from`부터 7일씩 `points`를 생성하며 접수가 없는 주는 0건을 반환합니다. 제품과 불량 대분류로 필터링할 수 있습니다. 필터 옵션은 `/api/meta`의 `products[].code`와 `defect_types`를 사용합니다. 대책 시행 표시는 QUALITY에 제공됩니다.

### 반복 불량 패턴

- QUALITY 계정은 `GET /api/patterns?product=PCB-ECU6-A1`로 선택 기간에 접수 두 건 이상이 연결된 조사를 조회합니다. `content.patterns`에 순위, `content.causes`에 확정 원인 분포를 받습니다. 기본 기간은 서울 기준 최근 90일입니다.
- `GET /api/patterns/{patternId}`에서 해당 조사의 근거 RMA, 제조 Lot·설비, 연결 대책과 선택 기간의 원장 손실을 조회합니다. `pattern_id`는 v5의 `investigation_id`입니다. 공정 실행 이력은 근거 RMA의 Lot에서 확인합니다.

### 조사별 대책과 재발

- QUALITY 계정은 `GET /api/recurrence/actions?investigation_id=INV-25-0001`로 조사 대책을 조회합니다. 목록은 `content.items`에서 읽으며 행 ID는 `corrective_action_id`입니다. `recurrence_count`는 대책·품목별 최신 판정에서 확정 재발한 RMA 수입니다.
- `POST /api/recurrence/investigations/{investigationId}/actions`로 대책을 등록하고, `GET/PUT /api/recurrence/actions/{actionId}`로 개별 계획을 조회·수정합니다. 등록 재시도에 `Idempotency-Key`를 사용할 수 있습니다.
- `POST /api/recurrence/actions/{actionId}/implement`는 시행일을 기록하고 효과 검증 대기로 전환합니다. 시행했거나 검증·재발 이력이 있는 대책의 계획은 잠깁니다.
- `GET /api/recurrence/actions/{actionId}/trend`는 시행일 전후 주간 접수 추이와 `checks[]`의 지표별 전·목표·후 검증값을 반환합니다.
- `GET /api/recurrence/reviews?corrective_action_id=CA-000001&verdict=RECURRENCE`로 품목의 재발 판정 이력을 조회합니다. 응답의 `rma_id`로 관련 접수 상세를 열 수 있습니다.
- QUALITY는 `POST /api/rmas/{rmaId}/recurrence`에 `{"decision":"확산형 재발"}`처럼 보내 접수의 재발 여부를 확정합니다. 상세에서 최초 판정과 담당자를 조회할 수 있고, 같은 값으로 재요청하면 기존 판정을 반환합니다.

### 품질 알림

- 스케줄러가 신규 반품 접수, 고위험 QA 판정, 재발 원인 검토 대기, 확산형 재발 확정, 원인 판정 결과, 분석 결과를 30초마다 읽어 `app.alert`에 중복 없이 저장합니다. 읽음·즐겨찾기·숨김은 `app.alert_user_state`에 계정별로 저장합니다.
- 신규 반품 접수 알림은 Flyway V18 적용 이후에 등록된 RMA에 한 번 생성됩니다. AI 처리에 실패해도 이미 저장된 접수는 알림 대상입니다.
- QUALITY는 `GET /api/alerts?limit=5`로 알림과 미확인 수를 읽고, `after_id`로 신규 ID를 조회할 수 있습니다. 알림의 `rma_id`나 `recur_id`로 케이스·대책 상세에 연결할 수 있습니다.
- 급증은 같은 품번·불량 대분류의 최근 30일 접수 수와 직전 30일을 비교합니다. 반복 재발은 같은 조사에서 서로 다른 접수 2건이 재발로 확인되면 생성되며, `pattern_id`에 조사 ID가 들어갑니다.

### RMA 보고서

- QUALITY는 완료 또는 부분 성공 분석이 저장된 접수에서 보고서를 생성할 수 있습니다. 원인 후보와 개선안·회신 초안은 저장된 분석에서 가져오며, DB에서 확인된 RMA만 근거 링크에 넣습니다. 생성한 보고서는 재요청 시 재사용합니다.
- 이력은 `content.items`, 개별 본문은 `content.content`에서 읽습니다. 비교 초안은 별도로 만들고, 수정하거나 교체하면 기존 검토 확인이 초기화됩니다.
- 품질 RMA 상세의 `report`는 저장·검토 여부를 반환합니다. 불량 패턴 화면의 요약은 조사와 대책에 등록된 사실을 표시합니다.

### 불량 패턴 보고서

- QUALITY는 조사 번호와 조회 기간·제품 범위로 보고서를 생성하고, 저장된 이력과 근거 RMA 최대 세 건을 조회할 수 있습니다.
- 기존 조사 기록으로 보고서를 구성합니다. 담당 부서별 미리보기는 저장한 원본을 사용하며, 화면과 PDF 연동의 요청·응답은 [Spring API v7 변경 명세](docs/PATTERN_REPORT_API_V7.md)에 있습니다.

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
# FastAPI 연결 전에도 확인 가능한 Spring 조회 점검
python3 scripts/staging_smoke.py --base-url https://staging.example.com --core-only
python3 scripts/staging_smoke.py --base-url https://staging.example.com
# 승인된 스테이징에서만 테스트 접수 한 건을 생성·종료
python3 scripts/staging_smoke.py --base-url https://staging.example.com --e2e
# Spring 재시작 뒤 앞 단계에서 출력된 테스트 번호·해시로 다시 조회
python3 scripts/staging_smoke.py --base-url https://staging.example.com \
  --verify-rma "$STAGING_SMOKE_RMA_ID" --attachment-sha256 "$STAGING_SMOKE_SHA256"
# 배포 담당자가 FastAPI만 중단한 테스트 환경에서 조회 영향 확인
python3 scripts/staging_smoke.py --base-url https://staging.example.com --expect-ai-outage
```

기본 모드는 로그인·역할, 실제 FastAPI 상태, v5 고객사·제품과 샘플 조회를 검사합니다. 잘못된 입력, 미로그인, 권한 부족, 없는 접수의 HTTP 상태와 Spring 오류 코드를 확인합니다.

`--core-only`는 같은 로그인·기준 정보·샘플 조회·오류 코드 검사에 CS·QUALITY 대시보드 KPI·요약 검사를 더합니다. FastAPI 호출과 테스트 접수 생성은 생략합니다. 대시보드 요약 경로가 포함된 Spring 배포본에서 실행합니다.

`--expect-ai-outage`는 CS 테스트 계정으로 Spring의 AI 사용 불가 상태, 기존 접수 조회 성공, 유사 사례의 `503 AI_UNAVAILABLE`을 확인합니다. 이 모드는 FastAPI를 제어하지 않으며 업무 레코드를 변경하지 않습니다.

`--e2e`는 합성 테스트 접수 한 건으로 중복 방지, 첨부 보존, QA·분석, 채택한 원인 조회, 원인 확정 전후의 2차 회신 초안, 회신과 실행 이력 저장을 확인합니다. `--verify-rma`는 저장된 분석·회신·처리 이력과 원문을 재조회합니다. E2E 실행은 점검용 접수와 이력을 남깁니다. 기본 샘플은 v5 합성 접수 `RMA-08927`입니다. 스테이징 데이터가 다르면 같은 종류의 샘플을 `--sample-rma`로 지정합니다. 실패 시 해당 단계와 오류 코드를 표시하고 종료 코드 1을 반환합니다.

```sh
./gradlew test
```

Docker가 실행 중이어야 PostgreSQL·Redis 통합 테스트가 동작합니다. 테스트 데이터와 첨부는 임시 환경을 사용합니다.

작업 브랜치명, 한국어 커밋 제목, PR과 리뷰는 [백엔드 팀 규칙](docs/CONVENTION.md)을 따릅니다. `main`에는 PR을 통해 변경을 합치며, 머지하면 바로 배포됩니다.
