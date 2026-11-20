package com.u1_2.effect.config.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class RawTextExtractorTest {

    private final RawTextExtractor extractor = new RawTextExtractor();

    @Test
    void 텍스트의_한글과_줄바꿈을_그대로_읽는다() {
        String raw = "  ICT 검사 결과\r\n단락 확인\n";
        assertThat(extract("txt", "\uFEFF" + raw)).isEqualTo(raw);
    }

    @Test
    void 메일에서_인코딩된_일반_텍스트_본문을_우선하고_첨부를_제외한다() {
        String body = "ICT 검사에서 단락 발견";
        String encoded = Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
        String email = """
                Subject: 본문에 포함하지 않는 제목
                MIME-Version: 1.0
                Content-Type: multipart/mixed; boundary=outer

                --outer
                Content-Type: multipart/alternative; boundary=inner

                --inner
                Content-Type: text/plain; charset=UTF-8
                Content-Transfer-Encoding: base64

                %s
                --inner
                Content-Type: text/html; charset=UTF-8

                <p>HTML 대체 본문</p>
                --inner--
                --outer
                Content-Type: text/plain; charset=UTF-8
                Content-Disposition: attachment; filename=report.txt

                별도 첨부 파일
                --outer--
                """.formatted(encoded);

        assertThat(extract("eml", email)).isEqualTo(body);
    }

    @Test
    void 일반_텍스트가_없는_메일은_HTML_본문을_텍스트로_읽는다() {
        String email = """
                MIME-Version: 1.0
                Content-Type: text/html; charset=UTF-8

                <div>ICT &amp; AOI<br>단락 발견</div><script>숨은 코드</script>
                """;
        assertThat(extract("eml", email).strip()).isEqualTo("ICT & AOI\n단락 발견");
    }

    @Test
    void PDF의_텍스트_레이어를_읽는다() throws IOException {
        assertThat(extractor.extract(file("pdf", pdf("PCB defect found")), "pdf").strip())
                .isEqualTo("PCB defect found");
    }

    @Test
    void 비어_있거나_읽을_수_없는_원문은_같은_오류로_처리한다() throws IOException {
        assertRawTextEmpty("txt", " \r\n".getBytes(StandardCharsets.UTF_8));
        assertRawTextEmpty("txt", new byte[]{(byte) 0xFF});
        assertRawTextEmpty("eml", "Subject: No body\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        assertRawTextEmpty("pdf", pdf(null));
        assertRawTextEmpty("pdf", new byte[]{1, 2, 3});
    }

    private void assertRawTextEmpty(String extension, byte[] content) {
        assertThatThrownBy(() -> extractor.extract(file(extension, content), extension))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(RmaErrorCode.RAW_TEXT_EMPTY));
    }

    private String extract(String extension, String content) {
        String source = extension.equals("eml") ? content.replace("\n", "\r\n") : content;
        return extractor.extract(file(extension, source.getBytes(StandardCharsets.UTF_8)), extension);
    }

    private MockMultipartFile file(String extension, byte[] content) {
        return new MockMultipartFile("raw_file", "claim." + extension, null, content);
    }

    private byte[] pdf(String content) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            if (content != null) {
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(content);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
