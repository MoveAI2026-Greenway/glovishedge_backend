package com.glovishedge.document.exception;

/**
 * 확장자는 맞지만 실제 내용이 손상됐거나 해당 포맷이 아닌 경우(예: fake.pdf). 사용자 입력 오류 — 400.
 */
public class DocumentParsingException extends RuntimeException {

    public DocumentParsingException(String message) {
        super(message);
    }

    public DocumentParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
