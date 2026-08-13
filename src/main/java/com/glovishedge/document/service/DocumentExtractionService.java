package com.glovishedge.document.service;

import com.glovishedge.document.dto.ExtractTextResponse;
import com.glovishedge.document.exception.DocumentParsingException;
import com.glovishedge.document.exception.NoExtractableTextException;
import com.glovishedge.document.exception.UnsupportedDocumentFormatException;
import com.glovishedge.document.extractor.DocumentTextExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 03_API계약.md §7 — 지원: PDF · DOCX · HWPX · TXT · MD, 최대 8MB(전역 multipart 설정에서 강제).
 * 원본 파일은 저장하지 않는다 — MultipartFile에서 바이트를 한 번만 읽어 알맞은
 * {@link DocumentTextExtractor}에 넘기고 버린다.
 */
@Service
public class DocumentExtractionService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "hwpx", "txt", "md");

    // 09_문구집.md §11 원문 그대로 — 화면/챗봇과 같은 문장을 쓴다.
    private static final String HWP_REJECTION_MESSAGE =
            "구형 HWP(.hwp)는 지원하지 않습니다. 텍스트가 깨진 채로 판정하면 틀린 답을 내놓게 되어 "
                    + "아예 받지 않습니다. 한글에서 다른 이름으로 저장 → HWPX 또는 PDF 로 내보내기 후 올려주세요.";

    private static final String SCANNED_PDF_MESSAGE =
            "텍스트를 추출하지 못했습니다. 스캔본(이미지) PDF 로 보입니다 — 텍스트가 포함된 PDF 로 올려주세요.";

    private final List<DocumentTextExtractor> extractors;

    public DocumentExtractionService(List<DocumentTextExtractor> extractors) {
        this.extractors = extractors;
    }

    public ExtractTextResponse extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UnsupportedDocumentFormatException("업로드된 파일이 비어 있습니다");
        }

        String extension = extractExtension(file.getOriginalFilename());

        if ("hwp".equals(extension)) {
            throw new UnsupportedDocumentFormatException(HWP_REJECTION_MESSAGE);
        }
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedDocumentFormatException(
                    "지원하지 않는 파일 형식입니다: ." + extension);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new DocumentParsingException("업로드된 파일을 읽는 데 실패했습니다", e);
        }

        DocumentTextExtractor extractor = extractors.stream()
                .filter(e -> e.supports(extension))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentFormatException(
                        "지원하지 않는 파일 형식입니다: ." + extension));

        String text = extractor.extract(content);

        if ("pdf".equals(extension) && isBlank(text)) {
            throw new NoExtractableTextException(SCANNED_PDF_MESSAGE);
        }

        return new ExtractTextResponse(text == null ? "" : text);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            throw new UnsupportedDocumentFormatException("파일 이름을 확인할 수 없습니다");
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            throw new UnsupportedDocumentFormatException("파일 확장자를 확인할 수 없습니다");
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
