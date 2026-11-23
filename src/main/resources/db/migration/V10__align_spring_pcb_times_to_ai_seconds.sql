DO $$
BEGIN
    -- FastAPI가 초 단위로 비교하는 PCB 시각만 맞춤. v5 원본 행과 app의 정밀 시각은 유지함
    IF to_regclass('pcb.rma_claim') IS NOT NULL THEN
        UPDATE pcb.rma_claim c
        SET opened_at = regexp_replace(c.opened_at, '[.][0-9]+([+-][0-9]{2}:[0-9]{2})$', '\1')
        FROM app.intake i
        WHERE c.rma_id = i.rma_id AND c.opened_at ~ '[.][0-9]+[+-][0-9]{2}:[0-9]{2}$';
    END IF;

    IF to_regclass('pcb.symptom_assessment') IS NOT NULL AND to_regclass('pcb.rma_item') IS NOT NULL THEN
        UPDATE pcb.symptom_assessment s
        SET created_at = regexp_replace(s.created_at, '[.][0-9]+([+-][0-9]{2}:[0-9]{2})$', '\1')
        FROM pcb.rma_item i JOIN app.intake intake ON intake.rma_id = i.rma_id
        WHERE s.rma_item_id = i.rma_item_id AND s.proposed_by = 'CS_AGENT'
            AND s.created_at ~ '[.][0-9]+[+-][0-9]{2}:[0-9]{2}$';
    END IF;

    IF to_regclass('pcb.qa_triage') IS NOT NULL THEN
        UPDATE pcb.qa_triage t
        SET decided_at = regexp_replace(t.decided_at, '[.][0-9]+([+-][0-9]{2}:[0-9]{2})$', '\1')
        WHERE t.decided_at ~ '[.][0-9]+[+-][0-9]{2}:[0-9]{2}$'
            AND (EXISTS (SELECT 1 FROM app.intake i WHERE i.rma_id = t.rma_id)
                 OR EXISTS (SELECT 1 FROM app.triage_history h WHERE h.rma_id = t.rma_id));
    END IF;
END
$$;
