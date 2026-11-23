package com.u1_2.effect.rma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.u1_2.effect.TestcontainersConfiguration;
import com.u1_2.effect.config.ai.AiErrorCode;
import com.u1_2.effect.config.ai.MockAiClient;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.config.jwt.JwtProvider;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.rma.application.analysis.RmaAnalysisPollingService;
import com.u1_2.effect.rma.application.intake.IntakeRegistrationService.Registration;
import com.u1_2.effect.rma.application.intake.IntakeRegistrationService;
import com.u1_2.effect.rma.domain.intake.DetectionStage;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.user.domain.Role;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"app.clock.fixed=2025-12-31T09:00:00+09:00", "app.analysis.polling.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(scripts = {"/sql/meta-fixture.sql", "/sql/rma-intake-fixture.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class IntakeRegistrationIntegrationTest {

    private static final AuthUser CS = new AuthUser(7L, Role.CS);
    private static final String RAW_TEXT = "ICT 검사에서 단락이 발견됐습니다.";

    @TempDir static Path storageDirectory;

    @Autowired IntakeRegistrationService service;
    @Autowired JdbcClient jdbcClient;
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired JsonMapper jsonMapper;
    @Autowired RmaAnalysisPollingService pollingService;
    @MockitoSpyBean MockAiClient aiClient;

    @DynamicPropertySource
    static void attachmentDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.attachments.directory", () -> storageDirectory.toString());
    }

    @Test
    void 접수와_품목과_파일을_함께_저장한다() throws Exception {
        String email = "Content-Type: text/plain; charset=UTF-8\r\n\r\n" + RAW_TEXT;
        var rawEmail = new MockMultipartFile("raw_file", "report.EML", "message/rfc822",
                email.getBytes(StandardCharsets.UTF_8));
        var registration = registration("LT-01603", LocalDate.of(2025, 12, 30),
                rawEmail, List.of(raw("extra.txt")));
        var result = service.register(registration, CS, "intake-save");
        String rmaId = result.rmaId();

        assertThat(result.created()).isTrue();
        assertThat(rmaId.compareTo("RMA-10610")).isPositive();
        assertThat(jdbcClient.sql("""
                SELECT external_claim_id, opened_at, occurred_at, raw_text, cs_status, qa_status
                FROM pcb.rma_claim WHERE rma_id = :id
                """).param("id", rmaId).query().singleRow())
                .containsEntry("external_claim_id", rmaId)
                .containsEntry("cs_status", "OPEN")
                .containsEntry("qa_status", "RECEIVED")
                .containsEntry("opened_at", "2025-12-31T09:00:00+09:00")
                .containsEntry("occurred_at", "2025-12-30T00:00:00+09:00")
                .containsEntry("raw_text", RAW_TEXT);
        assertThat(jdbcClient.sql("""
                SELECT rma_item_id, lot_id, trace_status, returned_qty, verified_defect_qty
                FROM pcb.rma_item WHERE rma_id = :id
                """).param("id", rmaId).query().singleRow())
                .containsEntry("rma_item_id", rmaId.replace("RMA-", "ITEM-"))
                .containsEntry("lot_id", "LT-01603")
                .containsEntry("trace_status", "LOT_LABEL")
                .containsEntry("returned_qty", 0L)
                .containsEntry("verified_defect_qty", null);
        assertThat(jdbcClient.sql("SELECT intake_state FROM app.intake WHERE rma_id = :id")
                .param("id", rmaId).query(String.class).single()).isEqualTo("PROCESSING");
        var attachments = jdbcClient.sql("SELECT kind, storage_path FROM app.attachment WHERE rma_id = :id ORDER BY id")
                .param("id", rmaId).query().listOfRows();
        assertThat(attachments).hasSize(2);
        assertThat(attachments.getFirst()).containsEntry("kind", "RAW");
        assertThat(Files.readString(storageDirectory.resolve(attachments.getFirst().get("storage_path").toString())))
                .isEqualTo(email);
    }

    @Test
    void 같은_사용자의_중복_요청을_동시에_보내도_한_건만_저장한다() throws Exception {
        var request = registration(null, LocalDate.of(2025, 12, 30), raw("report.txt"), List.of());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.register(request, CS, "intake-duplicate"));
            var second = executor.submit(() -> service.register(request, CS, "intake-duplicate"));
            assertThat(first.get().rmaId()).isEqualTo(second.get().rmaId());
            assertThat(List.of(first.get().created(), second.get().created())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM pcb.rma_claim WHERE idempotency_key = 'intake-duplicate'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void 초기_검증_실패는_접수를_저장하지_않는다() {
        LocalDate date = LocalDate.of(2025, 12, 30);
        var empty = new MockMultipartFile("raw_file", "report.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.register(registration("LT-02000", date, raw("report.txt"), List.of()),
                CS, "invalid-lot")).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(RmaErrorCode.LOT_NOT_FOUND));
        assertThatThrownBy(() -> service.register(registration(null, date.plusDays(2), raw("report.txt"), List.of()),
                CS, "invalid-date")).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> service.register(registration(null, date, empty, List.of()),
                CS, "invalid-raw")).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(RmaErrorCode.RAW_TEXT_EMPTY));
        assertThatThrownBy(() -> service.register(registration(null, date, raw("report.exe"), List.of()),
                CS, "invalid-file")).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(RmaErrorCode.UNSUPPORTED_FILE));
        assertThatThrownBy(() -> service.register(registration(null, date, raw("report.txt"), List.of()),
                new AuthUser(8L, Role.QUALITY), "invalid-role")).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_DENIED));
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM pcb.rma_claim WHERE idempotency_key LIKE 'invalid-%'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void 첨부_저장에_실패하면_접수와_먼저_저장한_파일도_되돌린다() throws Exception {
        MultipartFile unreadable = new MockMultipartFile("attachments", "extra.txt", "text/plain", new byte[]{1}) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("테스트용 파일 읽기 실패");
            }
        };
        long previousFiles = countStoredFiles();
        var request = registration(null, LocalDate.of(2025, 12, 30), raw("report.txt"), List.of(unreadable));

        assertThatThrownBy(() -> service.register(request, CS, "intake-rollback")).isInstanceOf(UncheckedIOException.class);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM pcb.rma_claim WHERE idempotency_key = 'intake-rollback'")
                .query(Long.class).single()).isZero();
        assertThat(countStoredFiles()).isEqualTo(previousFiles);
    }

    @Test
    void 접수_API가_분류와_QA_판정을_저장하고_분석을_등록한다() throws Exception {
        var response = mockMvc.perform(intake(RAW_TEXT, "api-intake", Role.CS)
                        .file(new MockMultipartFile("attachments[]", "photo.txt", "text/plain", new byte[]{1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"))
                .andExpect(jsonPath("$.content.qa_status").value("ANALYZING"))
                .andExpect(jsonPath("$.content.symptom.primary_label").value("ELEC02"))
                .andExpect(jsonPath("$.content.triage.analysis_level").value("L3"))
                .andExpect(jsonPath("$.content.analysis.analysis_state").value("QUEUED"))
                .andExpect(jsonPath("$.content.attachments[1].file_name").value("photo.txt"))
                .andReturn().getResponse();
        String rmaId = jsonMapper.readTree(response.getContentAsByteArray()).at("/content/rma_id").stringValue();
        assertThat(jdbcClient.sql("""
                SELECT s.intake_label, s.label_version, s.status, q.decided_by
                FROM pcb.symptom_assessment s JOIN pcb.rma_item i ON s.rma_item_id = i.rma_item_id
                JOIN pcb.qa_triage q ON i.rma_id = q.rma_id WHERE i.rma_id = :id
                """).param("id", rmaId).query().singleRow()).containsEntry("intake_label", "ELEC02")
                .containsEntry("label_version", "pcb-v3").containsEntry("status", "PROPOSED")
                .containsEntry("decided_by", "QA_AGENT");

        mockMvc.perform(intake(RAW_TEXT, "api-intake", Role.CS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.rma_id").value(rmaId))
                .andExpect(jsonPath("$.content.attachments[1].file_name").value("photo.txt"));
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM app.ai_analysis WHERE rma_id = :id")
                .param("id", rmaId).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void 정보_부족과_임시_판정은_분석을_등록하지_않는다() throws Exception {
        mockMvc.perform(intake("정보 부족", "api-needs-info", Role.CS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.content.follow_up_questions[0]").isString())
                .andExpect(jsonPath("$.content.analysis").isEmpty());
        mockMvc.perform(intake("임시 판정", "api-provisional", Role.CS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("TRIAGE_REVIEW"))
                .andExpect(jsonPath("$.content.triage.provisional").value(true))
                .andExpect(jsonPath("$.content.analysis").isEmpty());
        assertThat(jdbcClient.sql("""
                SELECT i.triage_proposal_json IS NOT NULL AND q.triage_id IS NULL
                FROM pcb.rma_claim c JOIN app.intake i ON i.rma_id = c.rma_id
                LEFT JOIN pcb.qa_triage q ON q.rma_id = c.rma_id WHERE c.idempotency_key = 'api-provisional'
                """).query(Boolean.class).single()).isTrue();
    }

    @Test
    void 고객_보충_회신을_한_번_저장하고_원문을_유지한_채_재분류한다() throws Exception {
        String rmaId = intakeNeedingInfo("supplement-success");
        String replyText = "ICT 검사에서 단락이 발견됐습니다.";

        reply(rmaId, replyText, "supplement-1", Role.QUALITY).andExpect(status().isForbidden());
        reply(rmaId, " ", "supplement-1", Role.CS).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        reply(rmaId, replyText, "supplement-1", Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.rma_id").value(rmaId))
                .andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"))
                .andExpect(jsonPath("$.content.cs_status").value("OPEN"))
                .andExpect(jsonPath("$.content.attachments").isNotEmpty());
        reply(rmaId, replyText, "supplement-1", Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"));
        reply(rmaId, "다른 회신", "supplement-1", Role.CS).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTAKE_REPLY_REQUEST_CONFLICT"));
        reply(rmaId, replyText, "supplement-2", Role.CS).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTAKE_NOT_NEEDS_INFO"));
        reply("RMA-UNKNOWN", replyText, "supplement-2", Role.CS).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RMA_NOT_FOUND"));

        assertThat(jdbcClient.sql("""
                SELECT c.raw_text, m.message_text, m.author_role, m.audience, meta.source
                FROM pcb.rma_claim c JOIN pcb.communication m ON m.rma_id = c.rma_id
                JOIN app.communication_meta meta ON meta.message_id = m.message_id WHERE c.rma_id = :id
                """).param("id", rmaId).query().singleRow()).containsEntry("raw_text", "정보 부족")
                .containsEntry("message_text", replyText).containsEntry("author_role", "customer")
                .containsEntry("audience", "CS").containsEntry("source", "CUSTOMER_SUPPLEMENT");
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM app.ai_analysis WHERE rma_id = :id")
                .param("id", rmaId).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void 분류가_실패해도_저장한_보충_회신을_재처리에서_쓴다() throws Exception {
        String rmaId = intakeNeedingInfo("supplement-fail");
        mockMvc.perform(get("/api/rmas/{id}", rmaId).contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.follow_up_questions[0]").value("불량이 발생한 공정과 확인한 증상을 알려주세요."))
                .andExpect(jsonPath("$.content.error").doesNotHaveJsonPath());
        String first = "공정 정보 부족";
        String second = "ICT 검사에서 단락이 발생했고 불량은 10개입니다.";
        reply(rmaId, first, "first", Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.intake_state").value("NEEDS_INFO"));
        String combined = "정보 부족\n\n[고객 보충 회신]\n" + first + "\n\n[고객 보충 회신]\n" + second;
        doThrow(new ApiException(AiErrorCode.AI_UNAVAILABLE)).doCallRealMethod().when(aiClient)
                .classify(argThat(input -> input != null && input.rmaId().equals(rmaId) && input.rawText().equals(combined)));

        reply(rmaId, second, "second", Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.intake_state").value("FAILED"))
                .andExpect(jsonPath("$.content.error.code").value("AI_UNAVAILABLE"));
        mockMvc.perform(get("/api/rmas/{id}", rmaId).contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.error.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.content.error.detail").value("분류 또는 QA 판정에 필요한 AI 응답을 받지 못했습니다."))
                .andExpect(jsonPath("$.content.follow_up_questions").doesNotHaveJsonPath());
        reply(rmaId, second, "second", Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.intake_state").value("FAILED"));
        mockMvc.perform(post("/api/rmas/{id}/intake/retry", rmaId)
                        .contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"));
        mockMvc.perform(get("/api/rmas/{id}", rmaId).contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.error").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.follow_up_questions").doesNotHaveJsonPath());
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM app.communication_meta WHERE rma_id = :id")
                .param("id", rmaId).query(Long.class).single()).isEqualTo(2);
    }

    private String intakeNeedingInfo(String key) throws Exception {
        var response = mockMvc.perform(intake("정보 부족", key, Role.CS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("NEEDS_INFO"))
                .andReturn().getResponse();
        return jsonMapper.readTree(response.getContentAsByteArray()).at("/content/rma_id").stringValue();
    }

    private ResultActions reply(String rmaId, String messageText, String key, Role role) throws Exception {
        return mockMvc.perform(post("/api/rmas/{id}/intake/reply", rmaId)
                .contextPath("/api").header("Authorization", token(role)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.createObjectNode().put("message_text", messageText).toString()));
    }

    @Test
    void AI_장애가_나도_접수와_완료한_판정이_남는다() throws Exception {
        doThrow(new ApiException(AiErrorCode.AI_UNAVAILABLE)).when(aiClient)
                .classify(argThat(input -> input != null && input.rawText().equals("분류 장애")));
        doThrow(new ApiException(AiErrorCode.AI_UNAVAILABLE)).when(aiClient)
                .registerAnalysis(argThat(input -> input != null && input.rawText().equals("분석 등록 장애")));

        mockMvc.perform(intake("분류 장애", "api-classify-fail", Role.CS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("FAILED"))
                .andExpect(jsonPath("$.content.error.code").value("AI_UNAVAILABLE"));
        mockMvc.perform(intake("분석 등록 장애", "api-analysis-fail", Role.CS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("FAILED"))
                .andExpect(jsonPath("$.content.qa_status").value("TRIAGED"))
                .andExpect(jsonPath("$.content.analysis.analysis_state").value("FAILED"));
    }

    @Test
    void AI_장애가_아닌_예외로_접수_처리가_끊겨도_실패로_남기고_재처리할_수_있다() throws Exception {
        doThrow(new ApiException(CommonErrorCode.INVALID_INPUT)).doCallRealMethod().when(aiClient)
                .classify(argThat(input -> input != null && input.rawText().equals("분류 계약 오류")));

        var response = mockMvc.perform(intake("분류 계약 오류", "api-classify-unexpected", Role.CS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("FAILED"))
                .andExpect(jsonPath("$.content.error.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse();
        String rmaId = jsonMapper.readTree(response.getContentAsByteArray()).at("/content/rma_id").stringValue();

        mockMvc.perform(post("/api/rmas/{id}/intake/retry", rmaId).contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"))
                .andExpect(jsonPath("$.content.error").doesNotExist());
    }

    @Test
    void QA_확인_후_분석_등록이_예외로_끊겨도_실패로_남기고_재처리할_수_있다() throws Exception {
        String rmaId = startAnalysis("triage-unexpected", "임시 판정 후 등록 예외").rmaId();
        doThrow(new IllegalStateException("등록 응답 해석 실패")).doCallRealMethod().when(aiClient)
                .registerAnalysis(argThat(input -> input != null && input.rmaId().equals(rmaId)));

        updateTriage(rmaId, Role.QUALITY, "L3", "판정 근거를 확인했습니다.")
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("FAILED"))
                .andExpect(jsonPath("$.content.qa_status").value("TRIAGED"));
        mockMvc.perform(post("/api/rmas/{id}/intake/retry", rmaId).contextPath("/api").header("Authorization", token(Role.QUALITY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"))
                .andExpect(jsonPath("$.content.triage.rationale").value("판정 근거를 확인했습니다."));
    }

    @Test
    void 접수_API는_CS와_필수_폼_값을_검증한다() throws Exception {
        mockMvc.perform(intake(RAW_TEXT, "api-quality", Role.QUALITY)).andExpect(status().isForbidden());
        mockMvc.perform(intake(RAW_TEXT, "api-invalid-form", Role.CS, "2099-01-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(multipart("/api/intake").contextPath("/api")).andExpect(status().isUnauthorized());
    }

    @Test
    void 분석_진행과_완료가_DB에_저장되고_CS에게는_허용한_필드만_보낸다() throws Exception {
        var analysis = startAnalysis("analysis-success", RAW_TEXT);
        findAnalysis(analysis.rmaId(), Role.QUALITY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.analysis_state").value("QUEUED"));
        pollNow(analysis.analysisId());
        findAnalysis(analysis.rmaId(), Role.QUALITY)
                .andExpect(jsonPath("$.content.analysis_state").value("RUNNING"))
                .andExpect(jsonPath("$.content.progress.current_agent").value("MANUFACTURING"));
        findAnalysis(analysis.rmaId(), Role.CS)
                .andExpect(jsonPath("$.content.progress.steps_done").value(1))
                .andExpect(jsonPath("$.content.progress.current_agent").doesNotHaveJsonPath());

        for (int i = 0; i < 3; i++) {
            pollNow(analysis.analysisId());
        }
        findAnalysis(analysis.rmaId(), Role.QUALITY)
                .andExpect(jsonPath("$.content.analysis_state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.content.result.cause_candidates[0].cause_code").isString())
                .andExpect(jsonPath("$.content.trace").isArray())
                .andExpect(jsonPath("$.content.as_of").isString())
                .andExpect(jsonPath("$.content.updated_at").value("2025-12-31T09:00:00+09:00"));
        findAnalysis(analysis.rmaId(), Role.CS)
                .andExpect(jsonPath("$.content.result.drafts.service_action.board_qty").value(3))
                .andExpect(jsonPath("$.content.result.drafts.service_action.expected_days_basis").isString())
                .andExpect(jsonPath("$.content.result.qa_review.analysis_level").value("L3"))
                .andExpect(jsonPath("$.content.result.qa_review.severity").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.result.cause_candidates").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.result.candidate_lots").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.trace").doesNotHaveJsonPath());
        assertThat(jdbcClient.sql("SELECT approval_status FROM app.cause_decision WHERE analysis_id = :id")
                .param("id", analysis.analysisId()).query(String.class).single()).isEqualTo("WAITING");
    }

    @Test
    void 분석_부분_완료는_결과를_보존하고_실패는_내부_오류_내용을_CS에_숨긴다() throws Exception {
        var partial = startAnalysis("analysis-partial", RAW_TEXT);
        var partialResponse = jsonMapper.readTree("""
                {"status":"PARTIAL","as_of":"2025-11-20T12:00:00+09:00","langsmith_run_id":"private-run",
                 "warnings":["내부 경고"],"result":{"qa_review":{"analysis_level":"L3"},
                 "drafts":{"service_action":null}}}
                """);
        doReturn(Optional.of(partialResponse)).when(aiClient).getAnalysis(partial.analysisId());
        pollNow(partial.analysisId());
        findAnalysis(partial.rmaId(), Role.QUALITY)
                .andExpect(jsonPath("$.content.analysis_state").value("PARTIAL"))
                .andExpect(jsonPath("$.content.langsmith_run_id").value("private-run"))
                .andExpect(jsonPath("$.content.warnings[0]").value("내부 경고"));
        findAnalysis(partial.rmaId(), Role.CS)
                .andExpect(jsonPath("$.content.result.drafts.service_action").hasJsonPath())
                .andExpect(jsonPath("$.content.result.drafts.service_action").isEmpty())
                .andExpect(jsonPath("$.content.langsmith_run_id").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content.warnings").isEmpty());

        var failed = startAnalysis("analysis-failed", RAW_TEXT);
        doReturn(Optional.of(jsonMapper.readTree("""
                {"status":"FAILED","error":{"code":"AGENT_FAILED","detail":"내부 에러"}}
                """))).when(aiClient).getAnalysis(failed.analysisId());
        pollNow(failed.analysisId());
        findAnalysis(failed.rmaId(), Role.QUALITY)
                .andExpect(jsonPath("$.content.error.detail").value("내부 에러"));
        findAnalysis(failed.rmaId(), Role.CS)
                .andExpect(jsonPath("$.content.analysis_state").value("FAILED"))
                .andExpect(jsonPath("$.content.error.code").value("AGENT_FAILED"))
                .andExpect(jsonPath("$.content.error.detail").value("원인분석을 완료하지 못했습니다."));
    }

    @Test
    void 분석이_없는_접수와_없는_접수_그리고_시간초과와_AI_작업_유실을_구분한다() throws Exception {
        findAnalysis("RMA-10610", Role.CS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.analysis_state").value("NOT_REQUESTED"));
        findAnalysis("RMA-99999", Role.QUALITY).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RMA_NOT_FOUND"));
        mockMvc.perform(get("/api/rmas/RMA-10610/analysis").contextPath("/api"))
                .andExpect(status().isUnauthorized());

        var timeout = startAnalysis("analysis-timeout", RAW_TEXT);
        jdbcClient.sql("UPDATE app.ai_analysis SET poll_started_at = CURRENT_TIMESTAMP - INTERVAL '41 seconds' "
                       + "WHERE analysis_id = :id").param("id", timeout.analysisId()).update();
        pollNow(timeout.analysisId());
        findAnalysis(timeout.rmaId(), Role.QUALITY)
                .andExpect(jsonPath("$.content.analysis_state").value("FAILED"))
                .andExpect(jsonPath("$.content.error.code").value("ANALYSIS_TIMEOUT"));

        var lost = startAnalysis("analysis-lost", RAW_TEXT);
        doReturn(Optional.empty()).when(aiClient).getAnalysis(lost.analysisId());
        pollNow(lost.analysisId());
        findAnalysis(lost.rmaId(), Role.QUALITY)
                .andExpect(jsonPath("$.content.analysis_state").value("FAILED"))
                .andExpect(jsonPath("$.content.error.code").value("ANALYSIS_LOST"));
    }

    private AnalysisIds startAnalysis(String key, String rawText) throws Exception {
        var response = mockMvc.perform(intake(rawText, key, Role.CS)).andExpect(status().isOk())
                .andReturn().getResponse();
        var content = jsonMapper.readTree(response.getContentAsByteArray()).path("content");
        return new AnalysisIds(content.path("rma_id").stringValue(), content.at("/analysis/analysis_id").asString(null));
    }

    @Test
    void 승인_목록은_기본_L3이고_L1과_L2의_대기와_확인을_구분한다() throws Exception {
        seedApproval("api-approval-l3", "L3", "WAITING");
        var l1 = seedApproval("api-approval-l1", "L1", "WAITING");
        var l2 = seedApproval("api-approval-l2", "L2", "WAITING");
        var confirmed = seedApproval("api-approval-confirmed", "L2", "CONFIRMED");

        mockMvc.perform(get("/api/approvals").contextPath("/api").header("Authorization", token(Role.QUALITY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.items[*].analysis_level", everyItem(is("L3"))));
        mockMvc.perform(get("/api/approvals").contextPath("/api").header("Authorization", token(Role.QUALITY))
                        .param("status", "WAITING").param("level", "L1_L2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.total").value(2))
                .andExpect(jsonPath("$.content.items[*].rma_id", hasItems(l1.rmaId(), l2.rmaId())))
                .andExpect(jsonPath("$.content.items[*].analysis_level", hasItems("L1", "L2")))
                .andExpect(jsonPath("$.content.items[*].customer_name", everyItem(is("고객사 A01"))))
                .andExpect(jsonPath("$.content.items[*].reported_part_no", everyItem(is("PCB-ECU6-A1"))))
                .andExpect(jsonPath("$.content.items[0].top_cause_code").isString());
        mockMvc.perform(get("/api/approvals").contextPath("/api").header("Authorization", token(Role.QUALITY))
                        .param("status", "CONFIRMED").param("level", "L1_L2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.total").value(1))
                .andExpect(jsonPath("$.content.items[0].rma_id").value(confirmed.rmaId()))
                .andExpect(jsonPath("$.content.items[0].customer_name").value("고객사 A01"))
                .andExpect(jsonPath("$.content.items[0].reported_part_no").value("PCB-ECU6-A1"));
        mockMvc.perform(get("/api/approvals").contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/approvals").contextPath("/api").header("Authorization", token(Role.QUALITY))
                        .param("level", "L2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 임시_QA_목록은_품질관리자에게만_열리고_페이지와_판정안을_내려준다() throws Exception {
        var pending = startAnalysis("api-review-queue", "임시 판정");
        mockMvc.perform(get("/api/triage-reviews").contextPath("/api").header("Authorization", token(Role.QUALITY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.page").value(1))
                .andExpect(jsonPath("$.content.items[*].rma_id", hasItems(pending.rmaId())))
                .andExpect(jsonPath("$.content.items[*].proposal.analysis_level", everyItem(is("L3"))));
        mockMvc.perform(get("/api/triage-reviews").contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/triage-reviews").contextPath("/api").header("Authorization", token(Role.QUALITY))
                        .param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 실패_접수는_첨부와_이전_분석을_유지하고_분류부터_한번만_다시_한다() throws Exception {
        String rawText = "분석 등록 한 번 실패";
        doThrow(new ApiException(AiErrorCode.AI_UNAVAILABLE)).doCallRealMethod().when(aiClient)
                .registerAnalysis(argThat(input -> input != null && input.rawText().equals(rawText)));
        var failed = startAnalysis("api-retry", rawText);
        String rmaId = failed.rmaId();
        var result = mockMvc.perform(post("/api/rmas/{id}/intake/retry", rmaId).contextPath("/api")
                        .header("Authorization", token(Role.QUALITY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.rma_id").value(rmaId))
                .andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"))
                .andExpect(jsonPath("$.content.analysis.analysis_state").value("QUEUED"))
                .andExpect(jsonPath("$.content.attachments.length()").value(1)).andReturn();
        String latestId = jsonMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/content/analysis/analysis_id").stringValue();
        findAnalysis(rmaId, Role.CS).andExpect(jsonPath("$.content.analysis_id").value(latestId));
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM pcb.qa_triage WHERE rma_id = :id")
                .param("id", rmaId).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM app.ai_analysis WHERE rma_id = :id")
                .param("id", rmaId).query(Long.class).single()).isEqualTo(2);
        mockMvc.perform(post("/api/rmas/{id}/intake/retry", rmaId).contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INTAKE_NOT_FAILED"));
        mockMvc.perform(post("/api/rmas/RMA-99999/intake/retry").contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 첨부_다운로드는_원본과_한글_파일명을_돌려주고_없는_파일은_404로_응답한다() throws Exception {
        var registration = service.register(registration(null, LocalDate.of(2025, 12, 30),
                raw("고객 원문.txt"), List.of()), CS, "api-download");
        var listed = mockMvc.perform(get("/api/rmas/{id}/attachments", registration.rmaId()).contextPath("/api")
                        .header("Authorization", token(Role.CS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.items[0].file_name").value("고객 원문.txt"))
                .andReturn().getResponse();
        long id = jsonMapper.readTree(listed.getContentAsByteArray()).at("/content/items/0/attachment_id").longValue();
        var file = mockMvc.perform(get("/api/attachments/{id}", id).contextPath("/api").header("Authorization", token(Role.QUALITY)))
                .andExpect(status().isOk()).andExpect(content().bytes(RAW_TEXT.getBytes(StandardCharsets.UTF_8)))
                .andReturn().getResponse();
        assertThat(ContentDisposition.parse(file.getHeader(HttpHeaders.CONTENT_DISPOSITION)).getFilename())
                .isEqualTo("고객 원문.txt");
        mockMvc.perform(get("/api/attachments/{id}", id).contextPath("/api")).andExpect(status().isUnauthorized());
        String storageKey = jdbcClient.sql("SELECT storage_path FROM app.attachment WHERE id = :id")
                .param("id", id).query(String.class).single();
        Files.delete(storageDirectory.resolve(storageKey));
        mockMvc.perform(get("/api/attachments/{id}", id).contextPath("/api").header("Authorization", token(Role.CS)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));
    }

    @Test
    void 품질관리자의_동시_재분석은_하나를_등록하고_원인_확정_뒤에는_막는다() throws Exception {
        var previous = seedApproval("api-reanalysis", "L3", "REJECTED");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> reanalysis(previous.rmaId(), Role.QUALITY)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            var second = executor.submit(() -> reanalysis(previous.rmaId(), Role.QUALITY)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            String id = jsonMapper.readTree(first.get()).at("/content/analysis_id").stringValue();
            assertThat(id).isNotEqualTo(previous.analysisId());
            assertThat(jsonMapper.readTree(second.get()).at("/content/analysis_id").stringValue()).isEqualTo(id);
        }
        reanalysis(previous.rmaId(), Role.CS).andExpect(status().isForbidden());
        var approved = seedApproval("api-reanalysis-locked", "L3", "APPROVED");
        reanalysis(approved.rmaId(), Role.QUALITY).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAUSE_ALREADY_DECIDED"));
    }

    @Test
    void 분석_조회_중에는_DB_잠금을_쥐지_않고_그사이_종료된_분석에는_늦은_응답을_저장하지_않는다() throws Exception {
        var analysis = startAnalysis("poll-boundary", RAW_TEXT);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(aiClient).getAnalysis(analysis.analysisId());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var polling = executor.submit(() -> pollNow(analysis.analysisId()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            // AI 응답을 기다리는 동안 다른 요청이 같은 행을 바로 잠글 수 있음
            jdbcClient.sql("SELECT 1 FROM app.ai_analysis WHERE analysis_id = :id FOR UPDATE NOWAIT")
                    .param("id", analysis.analysisId()).query(Integer.class).single();
            jdbcClient.sql("""
                    UPDATE app.ai_analysis SET analysis_state = 'FAILED', completed_at = CURRENT_TIMESTAMP
                    WHERE analysis_id = :id
                    """).param("id", analysis.analysisId()).update();
            release.countDown();
            polling.get();
        }
        assertThat(jdbcClient.sql("SELECT analysis_state FROM app.ai_analysis WHERE analysis_id = :id")
                .param("id", analysis.analysisId()).query(String.class).single()).isEqualTo("FAILED");
    }

    @Test
    void 분석_등록_중에는_RMA_잠금을_쥐지_않고_등록이_실패하면_선점을_풀어_다시_등록할_수_있다() throws Exception {
        var previous = seedApproval("register-boundary", "L3", "REJECTED");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            throw new ApiException(AiErrorCode.AI_UNAVAILABLE);
        }).doCallRealMethod().when(aiClient)
                .registerAnalysis(argThat(input -> input != null && input.rmaId().equals(previous.rmaId())));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var failed = executor.submit(() -> reanalysis(previous.rmaId(), Role.QUALITY)
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_UNAVAILABLE")));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            jdbcClient.sql("SELECT 1 FROM pcb.rma_claim WHERE rma_id = :id FOR UPDATE NOWAIT")
                    .param("id", previous.rmaId()).query(Integer.class).single();
            assertThat(registrationLeases(previous.rmaId())).isEqualTo(1);
            release.countDown();
            failed.get();
        }
        assertThat(registrationLeases(previous.rmaId())).isZero();
        reanalysis(previous.rmaId(), Role.QUALITY).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.analysis_state").value("QUEUED"));
    }

    private long registrationLeases(String rmaId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM app.analysis_registration WHERE rma_id = :id")
                .param("id", rmaId).query(Long.class).single();
    }

    private ResultActions reanalysis(String rmaId, Role role) throws Exception {
        return mockMvc.perform(post("/api/rmas/{id}/analysis", rmaId).contextPath("/api")
                .header("Authorization", token(role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"B\",\"top_k\":3}"));
    }

    private AnalysisIds seedApproval(String key, String level, String approvalStatus) throws Exception {
        var analysis = startAnalysis(key, RAW_TEXT);
        for (int i = 0; i < 4; i++) {
            pollNow(analysis.analysisId());
        }
        jdbcClient.sql("UPDATE pcb.qa_triage SET analysis_level = :level WHERE rma_id = :id")
                .param("level", level).param("id", analysis.rmaId()).update();
        jdbcClient.sql("UPDATE app.cause_decision SET approval_status = :status WHERE analysis_id = :id")
                .param("status", approvalStatus).param("id", analysis.analysisId()).update();
        return analysis;
    }

    private String token(Role role) {
        return "Bearer " + jwtProvider.createAccessToken(7L, role);
    }

    @Test
    void 임시_QA_판정을_확인하면_담당자와_이력을_저장하고_분석을_한_건만_시작한다() throws Exception {
        String rmaId = startAnalysis("triage-confirm", "임시 판정").rmaId();
        jdbcClient.sql("""
                UPDATE app.intake SET triage_proposal_json = jsonb_set(triage_proposal_json,
                    '{characteristic_symbol}', to_jsonb('SC'::text)) WHERE rma_id = :id
                """).param("id", rmaId).update();
        var detail = get("/api/rmas/{id}", rmaId).contextPath("/api");
        mockMvc.perform(detail.header("Authorization", token(Role.QUALITY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.triage").isEmpty())
                .andExpect(jsonPath("$.content.triage_proposal.analysis_level").value("L3"))
                .andExpect(jsonPath("$.content.triage_proposal.severity").isNumber())
                .andExpect(jsonPath("$.content.triage_proposal.characteristic_symbol").value("SC"))
                .andExpect(jsonPath("$.content.triage_proposal.rationale").isNotEmpty())
                .andExpect(jsonPath("$.content.triage_proposal.provisional").value(true));
        mockMvc.perform(get("/api/rmas/{id}", rmaId).contextPath("/api")
                        .header("Authorization", token(Role.CS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.triage_proposal").doesNotHaveJsonPath());
        updateTriage(rmaId, Role.CS, "L3", "판정 내용을 확인했습니다.").andExpect(status().isForbidden());
        updateTriage(rmaId, Role.QUALITY, "L2", "짧음").andExpect(status().isBadRequest());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> updateTriage(rmaId, Role.QUALITY, "L2", "판정 근거를 다시 확인했습니다.")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("ACCEPTED")));
            var second = executor.submit(() -> updateTriage(rmaId, Role.QUALITY, "L2", "판정 근거를 다시 확인했습니다.")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.decided_by").value("품질 담당자")));
            first.get();
            second.get();
        }

        findAnalysis(rmaId, Role.QUALITY).andExpect(jsonPath("$.content.analysis_state").value("QUEUED"));
        mockMvc.perform(detail)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.triage.analysis_level").value("L2"))
                .andExpect(jsonPath("$.content.triage_proposal").doesNotHaveJsonPath());
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM app.ai_analysis WHERE rma_id = :id")
                .param("id", rmaId).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM pcb.qa_triage WHERE rma_id = :id")
                .param("id", rmaId).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("""
                SELECT before_json->>'analysis_level' AS old_level, after_json->>'analysis_level' AS new_level,
                       changed_by FROM app.triage_history WHERE rma_id = :id ORDER BY id LIMIT 1
                """).param("id", rmaId).query().singleRow()).containsEntry("old_level", "L3")
                .containsEntry("new_level", "L2").containsEntry("changed_by", "품질 담당자");
    }

    @Test
    void 기존_판정_행을_갱신하면_승인_등급만_바뀌고_완료된_분석은_그대로_둔다() throws Exception {
        var job = seedApproval("triage-change", "L2", "WAITING");
        String originalTriage = jdbcClient.sql("SELECT triage_id FROM pcb.qa_triage WHERE rma_id = :id")
                .param("id", job.rmaId()).query(String.class).single();

        updateTriage(job.rmaId(), Role.QUALITY, "L3", "판정 상향")
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.analysis_level").value("L3"))
                .andExpect(jsonPath("$.content.decided_at").value("2025-12-31T09:00:00+09:00"));
        assertThat(jdbcClient.sql("SELECT triage_id FROM pcb.qa_triage WHERE rma_id = :id")
                .param("id", job.rmaId()).query(String.class).single()).isEqualTo(originalTriage);
        assertThat(jdbcClient.sql("SELECT analysis_level FROM app.cause_decision WHERE analysis_id = :id")
                .param("id", job.analysisId()).query(String.class).single()).isEqualTo("L3");
        findAnalysis(job.rmaId(), Role.QUALITY).andExpect(jsonPath("$.content.analysis_id").value(job.analysisId()))
                .andExpect(jsonPath("$.content.analysis_state").value("SUCCEEDED"));
        mockMvc.perform(get("/api/approvals").contextPath("/api").header("Authorization", token(Role.QUALITY)))
                .andExpect(jsonPath("$.content.items[*].rma_id", hasItems(job.rmaId())));
    }

    @Test
    void 원인_판정이_끝난_접수의_QA는_잠긴다() throws Exception {
        var job = seedApproval("triage-locked", "L3", "APPROVED");
        updateTriage(job.rmaId(), Role.QUALITY, "L3", "잠금 확인")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRIAGE_LOCKED"));
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM app.triage_history WHERE rma_id = :id")
                .param("id", job.rmaId()).query(Long.class).single()).isZero();
    }

    @Test
    void 수동_QA_확인_후_AI_장애를_재처리해도_담당자_판정은_유지한다() throws Exception {
        String rmaId = startAnalysis("triage-recovery", "임시 판정 후 등록 장애").rmaId();
        doThrow(new ApiException(AiErrorCode.AI_UNAVAILABLE)).doCallRealMethod().when(aiClient)
                .registerAnalysis(argThat(input -> input != null && input.rmaId().equals(rmaId)));
        updateTriage(rmaId, Role.QUALITY, "L3", "판정 근거를 확인했습니다.")
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("FAILED"))
                .andExpect(jsonPath("$.content.qa_status").value("TRIAGED"));
        mockMvc.perform(post("/api/rmas/{id}/intake/retry", rmaId).contextPath("/api").header("Authorization", token(Role.QUALITY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.intake_state").value("ACCEPTED"))
                .andExpect(jsonPath("$.content.triage.rationale").value("판정 근거를 확인했습니다."));
        assertThat(jdbcClient.sql("SELECT decided_by FROM pcb.qa_triage WHERE rma_id = :id")
                .param("id", rmaId).query(String.class).single()).isEqualTo("품질 담당자");
    }

    private ResultActions updateTriage(String rmaId, Role role, String level, String rationale) throws Exception {
        var body = jsonMapper.createObjectNode().put("severity", 8).putNull("characteristic_symbol")
                .put("analysis_level", level).put("rationale", rationale);
        return mockMvc.perform(put("/api/rmas/{id}/triage", rmaId).contextPath("/api")
                .header("Authorization", token(role)).contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private ResultActions findAnalysis(String id, Role role) throws Exception {
        return mockMvc.perform(get("/api/rmas/{id}/analysis", id).contextPath("/api")
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(7L, role)));
    }

    private void pollNow(String analysisId) {
        jdbcClient.sql("UPDATE app.ai_analysis SET next_poll_at = CURRENT_TIMESTAMP WHERE analysis_id = :id")
                .param("id", analysisId).update();
        pollingService.poll(analysisId);
    }

    private record AnalysisIds(String rmaId, String analysisId) {
    }

    private MockMultipartHttpServletRequestBuilder intake(String rawText, String key, Role role) {
        return intake(rawText, key, role, "2025-12-30");
    }

    private MockMultipartHttpServletRequestBuilder intake(String rawText, String key, Role role, String occurredAt) {
        return multipart("/api/intake")
                .file(new MockMultipartFile("raw_file", "claim.txt", "text/plain", rawText.getBytes(StandardCharsets.UTF_8)))
                .contextPath("/api")
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(7L, role))
                .header("Idempotency-Key", key)
                .param("customer_id", "A01").param("reported_part_no", "PCB-ECU6-A1")
                .param("occurred_at", occurredAt).param("detection_stage", "ICT_FUNCTION")
                .param("claimed_defect_qty", "3").param("input_qty", "500");
    }

    private long countStoredFiles() throws IOException {
        try (var paths = Files.walk(storageDirectory)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static MockMultipartFile raw(String name) {
        return new MockMultipartFile("raw_file", name, "text/plain", RAW_TEXT.getBytes(StandardCharsets.UTF_8));
    }

    private static Registration registration(
            String lot, LocalDate date, MultipartFile file, List<MultipartFile> attachments
    ) {
        return new Registration("A01", "PCB-ECU6-A1", date, DetectionStage.ICT_FUNCTION, 3, 500, lot,
                null, null, "대금 공제", file, attachments);
    }
}
