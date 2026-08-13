package com.glovishedge.document.exception;

/**
 * 지원하지 않는 확장자(.hwp 포함) 또는 빈 파일. 사용자 입력 오류 — 400.
 */
public class UnsupportedDocumentFormatException extends RuntimeException {

    public UnsupportedDocumentFormatException(String message) {
        super(message);
    }
}
