package com.u1_2.effect.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.global.dto.SuccessMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
