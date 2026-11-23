-- FastAPI 분석 등록을 기다리는 동안 같은 RMA의 중복 등록을 막는 선점 기록. 등록이 끝나면 지움
CREATE TABLE app.analysis_registration (
    rma_id TEXT PRIMARY KEY,
    lease_until TIMESTAMPTZ NOT NULL
);
