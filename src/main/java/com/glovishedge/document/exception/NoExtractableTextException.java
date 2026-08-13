package com.glovishedge.document.exception;

/**
 * 텍스트 레이어가 없는 PDF(스캔본 가능성) — 03_API계약.md §7 / 09_문구집.md §11.
 */
public class NoExtractableTextException extends RuntimeException {

    public NoExtractableTextException(String message) {
        super(message);
    }
}
