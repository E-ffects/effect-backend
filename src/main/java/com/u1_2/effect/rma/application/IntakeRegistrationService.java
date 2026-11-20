package com.u1_2.effect.rma.application;

import com.u1_2.effect.config.attachment.LocalAttachmentStorage;
import com.u1_2.effect.config.attachment.RawTextExtractor;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.rma.domain.CustomerRequest;
import com.u1_2.effect.rma.domain.DetectionStage;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.RmaIntakeRepository;
import com.u1_2.effect.rma.domain.RmaIntakeRepository.AttachmentRow;
import com.u1_2.effect.rma.domain.RmaIntakeRepository.IntakeRow;
import com.u1_2.effect.user.domain.Role;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 접수·품목·첨부를 한 트랜잭션에서 저장함. 이 단계가 성공해야 AI 처리를 시작함.
 */
@Service
@RequiredArgsConstructor
@Profile({"local", "test"})
@Transactional(readOnly = true)
public class IntakeRegistrationService {

    private static final int MAX_OTHER_ATTACHMENTS = 10;
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> RAW_TYPES = Set.of("eml", "txt", "pdf");

    private final RmaIntakeRepository repository;
    private final LocalAttachmentStorage storage;
    private final RawTextExtractor rawTextExtractor;
    private final Clock appClock;

    @Transactional
    public RegistrationResult register(Registration request, AuthUser user, String idempotencyKey) {
        if (user.role() != Role.CS) {
            throw new ApiException(AuthErrorCode.ACCESS_DENIED);
        }

        String key = StringUtils.hasText(idempotencyKey) ? idempotencyKey : UUID.randomUUID().toString();
        // 같은 사용자가 같은 키로 재요청하면 기존 접수 번호를 반환함
        Optional<String> existingId = repository.findRegisteredId(user.id(), key);
        if (existingId.isPresent()) {
            return new RegistrationResult(existingId.get(), false);
        }

        OffsetDateTime now = OffsetDateTime.now(appClock);
        String rawExtension = validate(request, now.toLocalDate());
        String lotId = blankToNull(request.reportedLotNo());
        if (lotId != null && !repository.lotMatchesPart(lotId, request.reportedPartNo())) {
            throw new ApiException(RmaErrorCode.LOT_NOT_FOUND);
        }
        String dateCode = blankToNull(request.reportedDateCode());
        // Lot이 가장 구체적인 추적 근거임. 없으면 날짜 코드만 남기고 둘 다 없으면 미추적으로 저장함
        String traceStatus = "UNTRACED";
        if (lotId != null) {
            traceStatus = "LOT_LABEL";
        } else if (dateCode != null) {
            traceStatus = "DATE_CODE_TRACED";
        }
        String rawText = rawTextExtractor.extract(request.rawFile(), rawExtension);

        repository.lockRegistration();
        // 원문을 읽는 동안 같은 키가 저장됐을 수 있어 잠금을 잡은 뒤 한 번 더 확인함
        existingId = repository.findRegisteredId(user.id(), key);
        if (existingId.isPresent()) {
            return new RegistrationResult(existingId.get(), false);
        }

        String number = String.format(Locale.ROOT, "%05d", repository.nextNumber());
        String rmaId = "RMA-" + number;
        String occurredAt = request.occurredAt().atStartOfDay(appClock.getZone()).toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        repository.insert(new IntakeRow(
                rmaId, "ITEM-" + number, request.customerId(), now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                occurredAt, rawText, request.reportedPartNo(), lotId, dateCode,
                request.detectionStage().name(), key, blankToNull(request.panelId()),
                request.inputQty(), request.claimedDefectQty(), traceStatus, blankToNull(request.customerRequest()),
                user.id(), now));

        storeAttachment(request.rawFile(), "RAW", rmaId, user.id(), now);
        for (MultipartFile file : request.attachments()) {
            storeAttachment(file, "OTHER", rmaId, user.id(), now);
        }
        return new RegistrationResult(rmaId, true);
    }

    private String validate(Registration request, LocalDate today) {
        String customerRequest = blankToNull(request.customerRequest());
        if (!StringUtils.hasText(request.customerId()) || !StringUtils.hasText(request.reportedPartNo())
                || request.occurredAt() == null || request.occurredAt().isAfter(today)
                || request.detectionStage() == null || request.claimedDefectQty() < 1
                || request.inputQty() < request.claimedDefectQty() || request.rawFile() == null
                || (customerRequest != null && !CustomerRequest.labels().contains(customerRequest))) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        if (!repository.customerExists(request.customerId()) || !repository.productExists(request.reportedPartNo())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }

        validateFile(request.rawFile());
        String filename = fileName(request.rawFile());
        String extension = StringUtils.getFilenameExtension(filename.toLowerCase(Locale.ROOT));
        if (extension == null || !RAW_TYPES.contains(extension) || request.attachments().size() > MAX_OTHER_ATTACHMENTS) {
            throw new ApiException(RmaErrorCode.UNSUPPORTED_FILE);
        }
        request.attachments().forEach(this::validateFile);
        return extension;
    }

    private void validateFile(MultipartFile file) {
        String name = fileName(file);
        if (!StringUtils.hasText(name) || file.getSize() > MAX_FILE_BYTES
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(RmaErrorCode.UNSUPPORTED_FILE);
        }
    }

    private void storeAttachment(MultipartFile file, String kind, String rmaId, long userId, OffsetDateTime now) {
        String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
        try (var source = file.getInputStream()) {
            String storagePath = storage.save(rmaId, source);
            repository.insertAttachment(new AttachmentRow(
                    rmaId, kind, fileName(file), contentType, file.getSize(), storagePath, userId, now));
        } catch (IOException e) {
            throw new UncheckedIOException("첨부 파일을 저장하지 못했습니다.", e);
        }
    }

    private String fileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        return original == null ? null : StringUtils.getFilename(original.replace('\\', '/'));
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    public record Registration(
            String customerId, String reportedPartNo, LocalDate occurredAt, DetectionStage detectionStage,
            long claimedDefectQty, long inputQty, String reportedLotNo, String reportedDateCode,
            String panelId, String customerRequest, MultipartFile rawFile, List<MultipartFile> attachments
    ) {
        public Registration {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    public record RegistrationResult(String rmaId, boolean created) {
    }
}
