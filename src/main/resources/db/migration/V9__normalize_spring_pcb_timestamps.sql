DO $$
BEGIN
    -- pcb 적재 전에도 Flyway를 시작할 수 있어야 함. 기존 Spring 작성 기록의 생략된 초만 복원함
    IF to_regclass('pcb.symptom_assessment') IS NOT NULL AND to_regclass('pcb.rma_item') IS NOT NULL THEN
        UPDATE pcb.symptom_assessment s
        SET created_at = regexp_replace(s.created_at, '(T[0-9]{2}:[0-9]{2})([+-][0-9]{2}:[0-9]{2})$', '\1:00\2')
        FROM pcb.rma_item i JOIN app.intake intake ON intake.rma_id = i.rma_id
        WHERE s.rma_item_id = i.rma_item_id AND s.proposed_by = 'CS_AGENT'
            AND s.created_at ~ 'T[0-9]{2}:[0-9]{2}[+-][0-9]{2}:[0-9]{2}$';
    END IF;

    IF to_regclass('pcb.qa_triage') IS NOT NULL THEN
        UPDATE pcb.qa_triage t
        SET decided_at = regexp_replace(t.decided_at, '(T[0-9]{2}:[0-9]{2})([+-][0-9]{2}:[0-9]{2})$', '\1:00\2')
        WHERE t.decided_at ~ 'T[0-9]{2}:[0-9]{2}[+-][0-9]{2}:[0-9]{2}$'
            AND (EXISTS (SELECT 1 FROM app.intake i WHERE i.rma_id = t.rma_id)
                 OR EXISTS (SELECT 1 FROM app.triage_history h WHERE h.rma_id = t.rma_id));
    END IF;
END
$$;
