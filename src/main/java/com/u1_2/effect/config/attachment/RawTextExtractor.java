package com.u1_2.effect.config.attachment;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부에서 분석에 쓸 본문만 읽음. 제품·수량처럼 구조화된 값은 접수 폼에서 따로 받음.
 */
@Component
public class RawTextExtractor {

    private static final Session MAIL_SESSION = Session.getInstance(new Properties());
    private static final int MAX_MAIL_DEPTH = 30;

    public String extract(MultipartFile file, String extension) {
        String text;
        try (InputStream input = file.getInputStream()) {
            text = switch (extension) {
                case "txt" -> StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(input.readAllBytes())).toString();
                case "eml" -> extractMail(input);
                case "pdf" -> extractPdf(input);
                default -> throw new ApiException(RmaErrorCode.UNSUPPORTED_FILE);
            };
        } catch (IOException | MessagingException e) {
            throw new ApiException(RmaErrorCode.RAW_TEXT_EMPTY);
        }

        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (!StringUtils.hasText(text) || text.indexOf('\0') >= 0) {
            throw new ApiException(RmaErrorCode.RAW_TEXT_EMPTY);
        }
        return text;
    }

    private String extractMail(InputStream input) throws MessagingException, IOException {
        // 메일 본문은 일반 텍스트 우선, 없으면 HTML에서 읽은 텍스트를 사용함
        var message = new MimeMessage(MAIL_SESSION, input);
        var plain = new StringBuilder();
        var html = new StringBuilder();
        appendMailBody(message, plain, html, 0);
        return StringUtils.hasText(plain) ? plain.toString() : html.toString();
    }

    private void appendMailBody(Part part, StringBuilder plain, StringBuilder html, int depth)
            throws MessagingException, IOException {
        if (depth > MAX_MAIL_DEPTH || Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || part.getFileName() != null) {
            return;
        }
        if (part.isMimeType("text/plain") && part.getContent() instanceof String text) {
            appendText(plain, text);
        } else if (part.isMimeType("text/html") && part.getContent() instanceof String text) {
            var body = Jsoup.parse(text).body();
            body.select("script, style").remove();
            appendText(html, body.wholeText());
        } else if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                appendMailBody(multipart.getBodyPart(i), plain, html, depth + 1);
            }
        }
    }

    private void appendText(StringBuilder body, String text) {
        if (StringUtils.hasText(text)) {
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append(text);
        }
    }

    private String extractPdf(InputStream input) throws IOException {
        // PDF는 내장 텍스트만 사용함. 스캔 파일에서 본문을 읽을 수 없으면 빈 원문으로 처리함
        try (var document = Loader.loadPDF(input.readAllBytes())) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw new ApiException(RmaErrorCode.RAW_TEXT_EMPTY);
            }
            return new PDFTextStripper().getText(document);
        }
    }
}
