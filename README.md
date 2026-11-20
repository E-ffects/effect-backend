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
| 접수·재처리 | `POST /api/intake`, `POST /api/rmas/{rmaId}/intake/retry` |
| QA 판정 | `GET /api/triage-reviews`, `PUT /api/rmas/{rmaId}/triage` |
| 분석·승인 목록 | `GET/POST /api/rmas/{rmaId}/analysis`, `GET /api/approvals` |
| 첨부 | `GET /api/rmas/{rmaId}/attachments`, `GET /api/attachments/{attachmentId}` |

인증이 필요한 요청은 `Authorization: Bearer <access_token>`을 보냅니다. 일반 응답은 `{code, message, content}` 형식이고, 첨부 다운로드는 원본 파일 본문을 반환합니다. 승인 목록은 기본 L3 대기 목록이며 `level=L1_L2`와 `status=WAITING` 또는 `CONFIRMED`를 조합할 수 있습니다.

## 로컬에서 흐름 확인

1. CS 토큰으로 접수 폼과 `raw_file`을 `POST /api/intake`에 보내면 접수 번호와 분석 등록 상태가 반환됩니다. 같은 사용자가 같은 `Idempotency-Key`로 다시 보내면 기존 접수 번호를 반환합니다.
2. 원문에 `정보 부족`을 넣으면 보충 질문을 반환합니다. `임시 판정`을 넣으면 QUALITY가 임시 QA 목록에서 확인한 다음 분석이 자동 시작됩니다.
3. `GET /api/rmas/{rmaId}/analysis`에서 진행 상태와 최종 결과를 확인합니다. QUALITY는 전체 결과를, CS는 회신 초안과 처리방안이 포함된 화면용 결과를 받습니다. 원문에 `처리 기록 없음`을 넣으면 처리방안은 `null`입니다.

이 브랜치의 구현 범위는 분석 결과 조회와 승인 대기 목록까지입니다. 원인 후보 승인·반려 저장, CS 회신·처리 종료, 실제 FastAPI와 운영 S3 연결은 후속 작업입니다.

## 테스트와 기여

```sh
./gradlew test
```

Docker가 실행 중이어야 PostgreSQL·Redis 통합 테스트가 동작합니다. 테스트 데이터와 첨부는 임시 환경을 사용합니다.

작업 브랜치명, 한국어 커밋 제목, PR과 리뷰는 [백엔드 팀 규칙](docs/CONVENTION.md)을 따릅니다. `develop`에는 PR을 통해 변경을 합칩니다.
