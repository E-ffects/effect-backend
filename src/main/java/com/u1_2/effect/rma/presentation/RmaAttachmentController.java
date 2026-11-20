package com.u1_2.effect.rma.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.RmaAttachmentService;
import com.u1_2.effect.rma.presentation.dto.RmaAttachmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 첨부 목록은 JSON으로, 실제 다운로드는 파일 본문으로 반환함.
 */
@RestController
@Profile({"local", "test"})
@RequiredArgsConstructor
public class RmaAttachmentController {

    private final RmaAttachmentService service;

    @Operation(summary = "RMA 첨부 목록 조회")
    @GetMapping("/rmas/{rmaId}/attachments")
    public ResponseEntity<ResponseDto<RmaAttachmentResponse>> getAttachments(
            @PathVariable String rmaId, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.getAttachments(rmaId, user.role())));
    }

    @Operation(summary = "RMA 첨부 파일 다운로드")
    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable long attachmentId, @CurrentUser AuthUser user) {
        var download = service.download(attachmentId, user.role());
        // 한글 파일명도 브라우저가 원본 이름으로 저장할 수 있게 내려줌
        var disposition = ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(download.contentType()).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(download.file());
    }
}
