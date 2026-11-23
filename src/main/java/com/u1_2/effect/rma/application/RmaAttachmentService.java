package com.u1_2.effect.rma.application;

import com.u1_2.effect.config.attachment.AttachmentStorage;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaAttachmentRepository;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.presentation.dto.RmaAttachmentResponse;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첨부 목록에는 화면용 메타데이터만 주고 다운로드 때만 저장소에서 원본을 읽음.
 */
@Service
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaAttachmentService {

    private final RmaClaimRepository claimRepository;
    private final RmaAttachmentRepository repository;
    private final AttachmentStorage storage;

    public RmaAttachmentResponse getAttachments(String rmaId, Role role) {
        RoleAccess.requireMember(role);
        claimRepository.requireExists(rmaId);
        var items = repository.findAttachments(rmaId).stream()
                .map(row -> new RmaAttachmentResponse.Attachment(row.attachmentId(), row.kind(), row.fileName(),
                        row.contentType(), row.size(), KoreaTime.inSeoul(row.uploadedAt())))
                .toList();
        return new RmaAttachmentResponse(items);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Download download(long attachmentId, Role role) {
        RoleAccess.requireMember(role);
        var stored = repository.findStoredFile(attachmentId)
                .orElseThrow(() -> new ApiException(RmaErrorCode.ATTACHMENT_NOT_FOUND));
        var file = storage.find(stored.storagePath())
                .orElseThrow(() -> new ApiException(RmaErrorCode.ATTACHMENT_NOT_FOUND));
        return new Download(stored.fileName(), contentType(stored.contentType()), file);
    }

    private MediaType contentType(String value) {
        try {
            MediaType parsed = MediaType.parseMediaType(value);
            return parsed.isConcrete() ? parsed : MediaType.APPLICATION_OCTET_STREAM;
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public record Download(String fileName, MediaType contentType, Resource file) {
    }
}
