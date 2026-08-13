package com.glovishedge.document.service;

import com.glovishedge.document.dto.ExtractTextResponse;
import com.glovishedge.document.exception.DocumentParsingException;
import com.glovishedge.document.exception.NoExtractableTextException;
import com.glovishedge.document.exception.UnsupportedDocumentFormatException;
import com.glovishedge.document.extractor.DocxTextExtractor;
import com.glovishedge.document.extractor.HwpxTextExtractor;
import com.glovishedge.document.extractor.PdfTextExtractor;
import com.glovishedge.document.extractor.PlainTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentExtractionServiceTest {

    private final DocumentExtractionService service = new DocumentExtractionService(List.of(
            new PdfTextExtractor(), new DocxTextExtractor(), new HwpxTextExtractor(), new PlainTextExtractor()));

    // 7. PDF text 추출
    @Test
    void extractsTextFromRealPdf() throws Exception {
        byte[] pdf = buildPdfWithText("Hello GlovisHEDGE PDF");
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", pdf);

        ExtractTextResponse response = service.extract(file);

        assertThat(response.text()).contains("Hello GlovisHEDGE PDF");
    }

    // 8. DOCX text 추출
    @Test
    void extractsTextFromRealDocx() throws Exception {
        byte[] docx = buildDocxWithText("Hello GlovisHEDGE DOCX");
        MockMultipartFile file = new MockMultipartFile("file", "contract.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

        ExtractTextResponse response = service.extract(file);

        assertThat(response.text()).contains("Hello GlovisHEDGE DOCX");
    }

    // 9. HWPX text 추출
    @Test
    void extractsTextFromRealHwpx() throws Exception {
        byte[] hwpx = buildHwpxWithText("Hello GlovisHEDGE HWPX");
        MockMultipartFile file = new MockMultipartFile("file", "contract.hwpx", "application/octet-stream", hwpx);

        ExtractTextResponse response = service.extract(file);

        assertThat(response.text()).contains("Hello GlovisHEDGE HWPX");
    }

    // 10. 손상 PDF
    @Test
    void corruptedPdf_isRejectedAsBadInput_notServerError() {
        byte[] fake = "this is not a real pdf".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", fake);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(DocumentParsingException.class);
    }

    // 11. 손상 DOCX
    @Test
    void corruptedDocx_isRejectedAsBadInput() {
        byte[] fake = "this is not a real docx".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "fake.docx", "application/octet-stream", fake);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(DocumentParsingException.class);
    }

    // 12. 손상 HWPX
    @Test
    void corruptedHwpx_isRejectedAsBadInput() {
        byte[] fake = "this is not a real hwpx".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "fake.hwpx", "application/octet-stream", fake);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(DocumentParsingException.class);
    }

    // 13. text layer 없는 PDF 처리 (스캔본 취급)
    @Test
    void pdfWithoutTextLayer_isTreatedAsScanned() throws Exception {
        byte[] pdf = buildBlankPdf();
        MockMultipartFile file = new MockMultipartFile("file", "scanned.pdf", "application/pdf", pdf);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(NoExtractableTextException.class)
                .hasMessageContaining("스캔본");
    }

    // 14. 빈 TXT — 0바이트 업로드는 "파일 비어 있음"으로 거부한다
    @Test
    void zeroByteTxt_isRejectedAsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(UnsupportedDocumentFormatException.class);
    }

    // 14. 빈 TXT — 공백만 있는 TXT는 PDF와 달리 "스캔본"으로 위장하지 않고 그대로(빈 텍스트) 성공 처리한다
    @Test
    void whitespaceOnlyTxt_isNotTreatedAsScanned_returnsAsIs() {
        MockMultipartFile file = new MockMultipartFile("file", "blank.txt", "text/plain",
                "   \n  ".getBytes(StandardCharsets.UTF_8));

        ExtractTextResponse response = service.extract(file);

        assertThat(response.text()).isEqualTo("   \n  ");
    }

    // 15. UTF-8 한글 TXT
    @Test
    void utf8KoreanTxt_isExtractedCorrectly() {
        byte[] content = "총비용은 운임과 탄소비용, 전쟁위험보험료의 합입니다.".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", content);

        ExtractTextResponse response = service.extract(file);

        assertThat(response.text()).isEqualTo("총비용은 운임과 탄소비용, 전쟁위험보험료의 합입니다.");
    }

    @Test
    void mdFile_isReadAsPlainText_notRendered() {
        byte[] content = "# 제목\n\n**굵게**".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "note.md", "text/markdown", content);

        ExtractTextResponse response = service.extract(file);

        assertThat(response.text()).isEqualTo("# 제목\n\n**굵게**");
    }

    @Test
    void hwpExtension_isRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "old.hwp", "application/x-hwp", "dummy".getBytes());

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(UnsupportedDocumentFormatException.class)
                .hasMessageContaining("HWP(.hwp)는 지원하지 않습니다");
    }

    @Test
    void unsupportedExtension_isRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "note.xlsx", "application/octet-stream", "dummy".getBytes());

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(UnsupportedDocumentFormatException.class);
    }

    // ---- fixtures ----

    private byte[] buildPdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildBlankPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocxWithText(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildHwpxWithText(String text) throws IOException {
        String sectionXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<hp:sec xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\">"
                + "<hp:p><hp:run><hp:t>" + text + "</hp:t></hp:run></hp:p>"
                + "</hp:sec>";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("Contents/section0.xml"));
            zip.write(sectionXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
