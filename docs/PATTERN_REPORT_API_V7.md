# Spring API v7 변경 명세

## 12. v6 → v7 불량 패턴 보고서

- 작성일은 2026-11-26이다. PCB v6 데이터의 조사·검사·대책 기록을 사용한다.
- 기존 단건 RMA 보고서 `/api/reports` 요청과 응답은 v6 그대로 사용한다. AI 보고서의 상단 전환 버튼에서 `단건 RMA`와 `불량 패턴`을 나눈다.
- 기존 `GET /api/dashboard/pattern-report/{patternId}`는 전환 기간에 호환용으로 유지한다. 새 화면의 `보고서 생성`은 아래 POST를 사용한다. 구 요약 API 삭제는 다른 화면의 호출을 제거한 뒤 진행한다.
- 신규 API는 모두 로그인한 `QUALITY` 권한이 필요하다. JSON 성공·오류는 기존 `ResponseDto` 규칙을 적용하며, 프론트 API 헬퍼가 언랩한 후에는 아래 표의 `content` 내부부터 읽는다.
- `pattern_id`는 조사 번호(`investigation_id`, 예: `INV-26-0021`), `report_id`는 저장된 문서의 숫자 ID다. `rma_id`는 패턴 부록에서 이동할 개별 사례 번호다.
- 보고서는 기존 PCB 기록으로 Spring에서 조립한다. 생성과 조회에 FastAPI 호출이 없으므로 AI 상태에 따라 신규 생성 버튼을 비활성화하지 않는다.

### 12-1. 엔드포인트

| 메서드·경로 | 요청 | `content` |
| --- | --- | --- |
| `POST /api/patterns/{patternId}/reports` | JSON `{ "from": "2026-10-03", "to": "2026-12-31", "product": null }` | `{ header, report, departments }` |
| `GET /api/pattern-reports` | 선택 `pattern_id`, `page` 기본 1, `size` 기본 30·최대 100 | `{ items: Header[], total, page, size }` |
| `GET /api/pattern-reports/{reportId}` | 숫자 `reportId` | `{ header, report, departments }` |
| `POST /api/pattern-reports/{reportId}/delivery-preview` | JSON `{ "department": "QUALITY", "request_note": "재검을 요청합니다.", "reply_due_date": "2027-01-06" }` | 12-4의 부서별 전달본 |

- 생성 시 `{ }`도 유효하다. `to` 생략 시 Spring 앱 기준일, `from` 생략 시 종료일을 포함한 최근 90일을 사용한다. 날짜는 서울 기준 양 끝 포함이며 최대 367일이다. `product`는 정확한 품번으로 필터하며, 공백·빈 문자열·`null`은 전체 제품이다. 화면에서 이미 선택한 기간과 제품은 생성 요청에 그대로 전달한다.
- 동일한 조사 번호·기간·제품으로 다시 요청하면 기존 저장본을 반환한다. 범위가 다르면 별도로 저장된다. 조회와 미리보기는 저장 당시의 데이터로 동작한다. 이번 계약에서 동일 범위 강제 재생성은 제공하지 않는다.
- 패턴 번호 검색은 `GET /api/pattern-reports?pattern_id=INV-26-0021`로 요청한다. 정확히 일치하는 저장본만 생성 시각과 ID 내림차순으로 반환한다. 목록이 비어 있으면 불량 패턴 분석 탭에서 생성하도록 안내한다. 저장본이 여러 개면 가장 최근 보고서를 먼저 표시하고 목록에서 기간·제품을 보고 다른 저장본을 열 수 있다.

### 12-2. 생성 및 상세 응답

| 필드 | 형태와 사용처 |
| --- | --- |
| `header` | `report_id`, `pattern_id`, `title`, `from`, `to`, `product`, `generated_by`, `generated_at`. 이력 목록의 각 행도 같은 헤더를 사용한다. 시각은 서울 오프셋을 포함한다. |
| `report.analysis_level`, `report.investigation_status` | 조사 등급과 상태 |
| `report.narrative` | `conclusion`, `trend`. 확정 판정과 발생 건수를 바탕으로 만든 기본 문장이다. |
| `report.totals` | `claims`, `lots`, `claimed_quantity`, `verified_quantity`, `investigation_loss_krw`, `unresolved_claims`. 건수는 RMA 중복 제거, 수량은 품목 합계다. 확인 수량의 `null`은 기록이 없는 경우다. 손실은 제품 필터와 별개인 선택 기간 **조사 전체** 원장 합계다. |
| `report.monthly[]` | `month`(`YYYY-MM`), `claims`. 접수일 기준이며 접수 없는 달은 프론트에서 0으로 보완할 수 있다. |
| `report.products[]` | `part_no`, `claims`, `lots`. 제품별 표와 CAD 모델 조회의 기준이다. |
| `report.lots[]`, `report.equipment[]` | 각각 `{ lot, count }`, `{ equipment_id, count }`. 동일 Lot·설비 내 동일 RMA는 한 번 센다. 연결 횟수는 원인 확정과 구별해서 표시한다. |
| `report.assessments[]` | `kind`(`OCCURRENCE` 또는 `DETECTION_ESCAPE`), `code`, `name`, `status`, `process_id`, `equipment_id`. 조사에 기록된 판정과 상태다. |
| `report.findings[]` | `kind`, `source_type`, `summary`. 판정에 연결된 공정·설비·비교시험 근거를 제공한다. |
| `report.process_flow[]` | `process_id`, `name`, `sequence`, `inspection`, `defect_occurrence`, `detection_escape`. 마지막 두 값은 확정 판정의 해당 단계만 `true`다. 주요 단계를 발췌할 때도 두 표시를 유지한다. |
| `report.actions[]` | `action_id`, `department`, `description`, `due_at`, `implemented_at`, `verification_status`, `checks[]`. 검증 항목은 `metric`, `baseline`, `target`, `measured`, `window_to`, `result`. 조사 단위 대책이며, 선택 기간의 종료일 안에 기록된 검증을 포함한다. |
| `report.materials[]` | `material_lot_id`, `material_type`, `supplier`, `inspection_result`, `affected_lots`. 연결된 생산 Lot의 자재 이력이며 자재 원인 확정 값으로 사용하지 않는다. |
| `report.evidence_cases[]` | 최대 3건. `rma_id`, `opened_on`, `part_no`, `detection_stage`, `qa_status`, `membership_reason`, `selection_reason`, `items[]`. 확정 원인에 연결된 반송품 검사 근거(`CONFIRMED_TEST`)를 우선하며 부족하면 실제 조사 연결 건(`LINKED_CASE`)을 추가한다. |
| `departments[]` | `code`, `name`, `recommended`. 조사에 해당 부서의 등록 대책이 있으면 `recommended=true`다. |

- 근거 RMA의 품목은 `lot_id`, `symptom`, `claimed_quantity`, `returned_quantity`, `verified_quantity`, `measurements[]`, `observations[]`를 가진다. 검사값은 `{ name, value, unit, result }`, 공정 관측값은 `{ process_id, equipment_id, item_code, value, unit, lower_limit, upper_limit, spc_flag }`다. 없는 값은 `null` 또는 빈 배열이다. 고객 원문과 회신 문안은 이 보고서 응답에 포함하지 않는다.
- 기준 시점에서 볼 수 있는 연결과 판정을 사용한다. 부서별 자료와 보고서 겉표지에는 집계 범위를 붙인다. 재발 관찰 중인 결과를 확정된 방지 효과로 표시하지 않는다.

### 12-3. 화면 및 PDF 연결

1. ‘불량 패턴 분석’에서 생성 성공 시 받은 `header.report_id`로 ‘AI 보고서’ 불량 패턴 모드를 열어 선택 상태를 유지한다.
2. 상단 `[단건 RMA] [불량 패턴]`을 전환하면 검색 라벨, 이력 API, 상세 본문을 함께 전환한다. 패턴의 경우 오른쪽의 `패턴 종합`과 `report.evidence_cases[].rma_id` 버튼으로 같은 문서 안에서 부록을 이동한다. 별도 단건 RMA 보고서로 이동하는 링크와 구분한다.
3. PDF 내보내기는 프론트 기존 `saveElementAsPdf`를 재사용한다. 상단 메뉴·탭·버튼을 제외한 내보내기용 문서에는 패턴 종합과 RMA 부록을 모두 렌더링한다. 전달본은 별도 문서 노드로 렌더링해 파일명을 `{pattern_id}_{부서명}_전달본.pdf`로 저장한다.
4. 첨부된 기존 보고서처럼 화면의 Pretendard를 사용한다. 내보내기 전에 `document.fonts.ready`와 이미지 `decode()`를 기다리고, 주 글꼴이 로드되지 않으면 사용자에게 다시 시도할 수 있게 표시한다. A4 인쇄 폭에서 본문·표의 줄바꿈을 확인하고, 제목만 다음 페이지에 남거나 표 헤더와 첫 행이 갈라지지 않게 페이지 나눔을 지정한다.
5. CAD는 프론트의 기존 `/pcb3d/{part_no}.glb` 모델에서 렌더링한 정적 이미지를 내보내기용 문서에 넣는다. 모델이 없으면 해당 그림 영역을 생략한다. CAD 레이어를 강조할 때 가정 치수 모델이며 실제 결함 좌표를 뜻하지 않는다는 설명을 붙인다.
6. 기존 첨부 PDF는 HTML을 이미지로 렌더링하는 내보내기이며 텍스트 레이어가 없다. 브라우저별로 JPG 캡처 및 결과 PDF에서 한글 자소·볼드·제목 줄바꿈, 표 잘림, 그래프, CAD 두 장면, 부록 전체, 페이지 번호를 확인한다. Spring에 바이너리 PDF 엔드포인트는 추가하지 않았다.

### 12-4. 부서별 전달본

| `department` | 표시 이름 | 선택한 내용 |
| --- | --- | --- |
| `PROCESS_ENGINEERING` | 공정기술팀 | 발생 원인, 공정·설비 근거, 사례별 공정 관측값 |
| `QUALITY` | 품질팀 | 검출 누락 판정, 사례별 반송품 재검 측정값, 효과 검증 |
| `EQUIPMENT` | 설비팀 | 설비 근거, 설비 이벤트, 사례별 공정 관측값 |
| `PRODUCTION` | 생산팀 | 연결 생산 Lot와 영향 접수 범위 |
| `PURCHASING` | 구매팀 | 자재 Lot, 공급사, 입고 검사, 연결 생산 Lot 수 |

- 전달본은 `{ header, department, narrative, totals, products, request_note, reply_due_date, assessments, findings, process_flow, lots, equipment, materials, actions, evidence_cases }`다. `department`는 이름·추천 여부를 포함한 객체다. 부서에 해당하지 않는 섹션은 빈 배열이며 화면에서 숨길 수 있다.
- `request_note`는 선택이며 2,000자까지 받는다. 공백이면 부서별 기본 요청을 제공한다. `reply_due_date`는 선택이고 `YYYY-MM-DD`로 받는다. 미리보기는 원본·검토 상태를 변경하지 않으며 이메일이나 사내 알림을 발송하지 않는다. 팝업 수정값으로 미리보기를 다시 요청한 후 PDF로 저장한다.

### 12-5. 오류 처리

| HTTP | 코드 | 처리 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 날짜 역전·최대 범위 초과·양수가 아닌 보고서 ID·허용되지 않는 부서 또는 잘못된 요청 형식 |
| 401 / 403 | 기존 인증·권한 코드 | 기존 재발급·권한 안내 적용 |
| 404 | `PATTERN_NOT_FOUND` | 생성하려는 조사 번호를 찾을 수 없음 |
| 404 | `PATTERN_REPORT_NOT_FOUND` | 상세 또는 전달본 요청 대상 저장본을 찾을 수 없음 |
| 409 | `PATTERN_REPORT_NO_CASES` | 선택한 기간·제품에 연결된 RMA가 없어 범위 변경이 필요함 |
