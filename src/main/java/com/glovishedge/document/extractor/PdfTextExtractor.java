package com.glovishedge.document.extractor;

import com.glovishedge.document.exception.DocumentParsingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 텍스트 레이어가 있는 일반 PDF만 다룬다. OCR은 하지 않는다 — 텍스트 레이어가 없는 결과(빈 문자열)는
 * Service에서 스캔본 PDF로 판정한다.
 */
@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public String extract(byte[] content) {
        if (!hasPdfMagicBytes(content)) {
            throw new DocumentParsingException("PDF 파일이 아니거나 손상되었습니다");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            throw new DocumentParsingException("PDF 파싱에 실패했습니다", e);
        }
    }

    private boolean hasPdfMagicBytes(byte[] content) {
        if (content.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (content[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
