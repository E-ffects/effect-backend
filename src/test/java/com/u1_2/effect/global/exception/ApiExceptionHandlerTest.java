package com.u1_2.effect.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.global.dto.SuccessMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(controllers = ApiExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandlerTest.TestController.class)
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 성공_응답() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("요청이 성공적으로 처리되었습니다."))
                .andExpect(jsonPath("$.content.user_name").value("홍길동")); // snake_case
    }

    @Test
    void 본문_없는_성공은_200과_null_content() throws Exception {
        mockMvc.perform(get("/test/no-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("삭제가 완료되었습니다."))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void 비즈니스_예외는_ErrorCode의_상태와_문구() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("테스트 리소스가 없습니다."))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void 검증_실패는_400_INVALID_INPUT_한_줄() throws Exception {
        mockMvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void JSON_파싱_실패는_400() throws Exception {
        mockMvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_경로는_404() throws Exception {
        mockMvc.perform(get("/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 허용되지_않은_메서드는_405() throws Exception {
        mockMvc.perform(delete("/test/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 예상하지_못한_예외는_500이고_메시지를_숨긴다() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("잠시 후 다시 시도하세요."));
    }

    @Test
    void 허용되지_않은_메서드는_Allow_헤더를_유지한다() throws Exception {
        mockMvc.perform(delete("/test/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }

    @Test
    void 필수_헤더_누락은_400() throws Exception {
        mockMvc.perform(get("/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 지원하지_않는_Content_Type은_400() throws Exception {
        mockMvc.perform(post("/test/valid").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 응답할_수_없는_Accept는_500이_아니다() throws Exception {
        var result = mockMvc.perform(get("/test/ok").accept(MediaType.APPLICATION_XML)).andReturn();
        assertThat(result.getResponse().getStatus()).isBetween(400, 499);
    }

    @Test
    void multipart_파트_누락은_400() throws Exception {
        mockMvc.perform(post("/test/upload").contentType(MediaType.MULTIPART_FORM_DATA).content("not multipart"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void multipart_파싱_실패는_첨부_오류() throws Exception {
        mockMvc.perform(get("/test/multipart-broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE"));
    }

    @Test
    void 데이터_무결성_위반은_409_CONFLICT() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void 메서드_보안_거부는_403_ACCESS_DENIED() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 클라이언트가_끊은_연결은_500_본문을_쓰지_않는다() throws Exception {
        var result = mockMvc.perform(get("/test/client-abort")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("INTERNAL_ERROR");
    }

    @RestController
    static class TestController {

        record UserView(String userName) {
        }

        record NameRequest(@NotBlank String name) {
        }

        @GetMapping("/test/ok")
        ResponseEntity<ResponseDto<UserView>> ok() {
            return ResponseEntity.ok(ResponseDto.ofSuccess(new UserView("홍길동")));
        }

        @GetMapping("/test/no-content")
        ResponseEntity<ResponseDto<Void>> noContent() {
            return ResponseEntity.ok(ResponseDto.ofSuccess(SuccessMessage.DELETE_SUCCESS));
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new ApiException(TestErrorCode.TEST_NOT_FOUND);
        }

        @PostMapping("/test/valid")
        void valid(@Valid @RequestBody NameRequest request) {
        }

        @GetMapping("/test/header")
        void header(@RequestHeader("X-Request-Key") String key) {
        }

        @PostMapping("/test/upload")
        void upload(@RequestPart("file") MultipartFile file) {
        }

        @GetMapping("/test/multipart-broken")
        void multipartBroken() {
            throw new MultipartException("Failed to parse multipart servlet request");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/test/denied")
        void denied() {
            throw new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false));
        }

        @GetMapping("/test/client-abort")
        void clientAbort() throws IOException {
            throw new ClientAbortException(new IOException("Broken pipe"));
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("내부 정보가 담긴 메시지");
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum TestErrorCode implements ErrorCode {
        TEST_NOT_FOUND(HttpStatus.NOT_FOUND, "TEST_NOT_FOUND", "테스트 리소스가 없습니다.");

        private final HttpStatus httpStatus;
        private final String errorCode;
        private final String message;
    }
}
