# 공용 PostgreSQL v5 점검

## 최초 적재와 기존 DB 확인

`pcb`는 v5 원본과 서비스에서 추가되는 접수·품목·QA 판정·회신을 함께 보관합니다. `app`은 접수 상태, 분석, 첨부 메타데이터 등을 보관합니다. 팀의 `effect-ai/db/postgres/build.py`가 만드는 `load.sql`은 빈 스키마 최초 적재용입니다. 기존 공용 DB에서 새 원본을 반영해야 할 때는 별도 DB에 적재한 뒤 대상 데이터와 충돌을 비교합니다. 실제 반영 방식은 데이터·배포 담당과 확정합니다.

Flyway 적용 후 저장소 루트에서 아래 점검을 실행합니다. `PGSERVICE`에는 운영자가 설정한 PostgreSQL 접속 프로필을 사용합니다. 역할 변수에는 실제 서비스의 PostgreSQL 로그인 역할을 지정합니다.

```sh
PGSERVICE=effect_staging psql -X \
  -v spring_role=effect_spring -v ai_role=effect_ai \
  -f scripts/postgres_readiness.sql
```

- `v5_missing=0`이면 v5 원본 접수 ID 10,610개가 모두 있습니다. `total_rmas`에는 신규 접수도 포함됩니다.
- `spring_data_access=t`이면 Spring의 주요 PCB·업무 테이블 쓰기 권한 확인을 통과했습니다.
- `ai_pcb_read_only=t`이면 AI는 PCB 테이블·뷰를 읽을 수 있고 PCB 변경, Spring 역할 전환, `app` 접근, 사용자 테이블 조회 권한 점검도 통과했습니다.
- 스크립트는 읽기 전용으로 실행되며 점검 실패 시 0이 아닌 종료 코드를 반환합니다. 실제 계정 연결과 접수 저장은 [스테이징 점검](../README.md#스테이징-배포-후-자동-점검)에서 확인합니다.

## 반영 전후와 복원 검증

1. 점검 시간 동안 대상 접수에 변경이 들어오지 않도록 하고 DB 백업을 확보합니다. 승인된 점검용 접수 중 원문 첨부와 분석·처리 이력이 있는 접수를 고릅니다.
2. 원본 DB에서 `before.csv`를 만들고, 별도 DB에 복원하거나 새 원본 반영을 연습합니다. 점검할 복원 DB를 가리키는 접속 프로필로 `after.csv`를 만듭니다.

```sh
PGSERVICE=effect_staging psql -X -q --csv -v rma_id="$CHECK_RMA_ID" \
  -f scripts/postgres_case_snapshot.sql > before.csv
PGSERVICE=effect_restore psql -X -q --csv -v rma_id="$CHECK_RMA_ID" \
  -f scripts/postgres_case_snapshot.sql > after.csv
diff -u before.csv after.csv
```

3. 행 건수와 지문이 같은지 확인합니다. 여러 건을 확인할 때는 같은 명령을 접수별로 반복합니다. CSV에는 원문·회신·첨부 경로 대신 테이블 이름, 행 건수와 지문이 들어갑니다.
4. DB에는 첨부 메타데이터와 저장 경로가 들어 있습니다. 첨부 파일 자체의 보존은 저장소에서 따로 확인합니다. 자동 점검에서 만든 종료 건이라면 `--verify-rma`와 기존 `--attachment-sha256`으로 실제 다운로드까지 검사할 수 있습니다.
