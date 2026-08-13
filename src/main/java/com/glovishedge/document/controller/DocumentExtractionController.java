package com.glovishedge.document.controller;

import com.glovishedge.document.dto.ExtractTextResponse;
import com.glovishedge.document.exception.DocumentParsingException;
import com.glovishedge.document.exception.NoExtractableTextException;
import com.glovishedge.document.exception.UnsupportedDocumentFormatException;
import com.glovishedge.document.service.DocumentExtractionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/extract-text")
public class DocumentExtractionController {

    private final DocumentExtractionService documentExtractionService;

    public DocumentExtractionController(DocumentExtractionService documentExtractionService) {
        this.documentExtractionService = documentExtractionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtractTextResponse extractText(@RequestParam("file") MultipartFile file) {
        return documentExtractionService.extract(file);
    }

    @ExceptionHandler(UnsupportedDocumentFormatException.class)
    public ResponseEntity<String> handleUnsupportedFormat(UnsupportedDocumentFormatException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(DocumentParsingException.class)
    public ResponseEntity<String> handleParsingFailure(DocumentParsingException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(NoExtractableTextException.class)
    public ResponseEntity<String> handleNoExtractableText(NoExtractableTextException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body("파일 크기가 8MB를 초과했습니다");
    }
}
