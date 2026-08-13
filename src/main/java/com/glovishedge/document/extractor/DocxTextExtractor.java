package com.glovishedge.document.extractor;

import com.glovishedge.document.exception.DocumentParsingException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * DOCX 본문 텍스트를 추출한다. {@link XWPFWordExtractor}가 문단·표 텍스트를 함께 뽑아준다.
 * 이미지 OCR · SmartArt · embedded object · tracked changes · 매크로는 다루지 않는다.
 */
@Component
public class DocxTextExtractor implements DocumentTextExtractor {

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    @Override
    public boolean supports(String extension) {
        return "docx".equals(extension);
    }

    @Override
    public String extract(byte[] content) {
        if (!hasZipMagicBytes(content)) {
            throw new DocumentParsingException("DOCX 파일이 아니거나 손상되었습니다");
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException | RuntimeException e) {
            throw new DocumentParsingException("DOCX 파싱에 실패했습니다", e);
        }
    }

    private boolean hasZipMagicBytes(byte[] content) {
        if (content.length < ZIP_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (content[i] != ZIP_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
