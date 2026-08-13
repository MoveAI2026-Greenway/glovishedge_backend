package com.glovishedge.document.controller;

import com.glovishedge.document.dto.ExtractTextResponse;
import com.glovishedge.document.exception.NoExtractableTextException;
import com.glovishedge.document.exception.UnsupportedDocumentFormatException;
import com.glovishedge.document.service.DocumentExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentExtractionController.class)
class DocumentExtractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentExtractionService documentExtractionService;

    // 1. 정상 TXT
    @Test
    void postTxt_returns200WithText() throws Exception {
        when(documentExtractionService.extract(any())).thenReturn(new ExtractTextResponse("hello"));

        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/extract-text").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("hello"));
    }

    // 2. 정상 MD
    @Test
    void postMd_returns200WithText() throws Exception {
        when(documentExtractionService.extract(any())).thenReturn(new ExtractTextResponse("# title"));

        MockMultipartFile file = new MockMultipartFile("file", "note.md", "text/markdown",
                "# title".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/extract-text").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("# title"));
    }

    // 3. 파일 없음
    @Test
    void noFilePart_returns400() throws Exception {
        mockMvc.perform(multipart("/api/extract-text"))
                .andExpect(status().isBadRequest());
    }

    // 4. 8MB 초과
    // MockMvc의 MockMultipartFile은 이미 만들어진 객체를 그대로 주입하므로, 실제 서블릿 컨테이너가
    // spring.servlet.multipart.max-file-size 초과 시 던지는 MaxUploadSizeExceededException 발생 경로를
    // 이 slice 테스트로는 재현할 수 없다. 대신 그 예외가 컨트롤러까지 올라왔을 때 우리 핸들러가
    // 413로 매핑하는지를 검증한다 — 실제 8MB 초과 업로드의 end-to-end 동작은 실제 서버 기동이 필요하다.
    @Test
    void maxUploadSizeExceeded_isMappedTo413() throws Exception {
        when(documentExtractionService.extract(any()))
                .thenThrow(new MaxUploadSizeExceededException(8 * 1024 * 1024));

        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain",
                "small enough content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/extract-text").file(file))
                .andExpect(status().isContentTooLarge());
    }

    // 5. 지원하지 않는 확장자
    @Test
    void unsupportedExtension_returns400() throws Exception {
        when(documentExtractionService.extract(any()))
                .thenThrow(new UnsupportedDocumentFormatException("지원하지 않는 파일 형식입니다: .xlsx"));

        MockMultipartFile file = new MockMultipartFile("file", "note.xlsx", "application/octet-stream",
                "dummy".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/extract-text").file(file))
                .andExpect(status().isBadRequest());
    }

    // 6. .hwp 거부
    @Test
    void hwpExtension_returns400WithRejectionMessage() throws Exception {
        when(documentExtractionService.extract(any()))
                .thenThrow(new UnsupportedDocumentFormatException("구형 HWP(.hwp)는 지원하지 않습니다."));

        MockMultipartFile file = new MockMultipartFile("file", "old.hwp", "application/x-hwp",
                "dummy".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/extract-text").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scannedPdf_returns422() throws Exception {
        when(documentExtractionService.extract(any()))
                .thenThrow(new NoExtractableTextException("텍스트를 추출하지 못했습니다. 스캔본(이미지) PDF 로 보입니다"));

        MockMultipartFile file = new MockMultipartFile("file", "scanned.pdf", "application/pdf",
                "%PDF-1.4 dummy".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/extract-text").file(file))
                .andExpect(status().isUnprocessableContent());
    }
}
