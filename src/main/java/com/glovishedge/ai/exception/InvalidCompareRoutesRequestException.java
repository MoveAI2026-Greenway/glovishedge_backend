package com.glovishedge.ai.exception;

/**
 * 요청이 03_API계약.md §4 계약을 위반함(routes 개수, status 값, statusReason 누락 등). 400.
 */
public class InvalidCompareRoutesRequestException extends RuntimeException {

    public InvalidCompareRoutesRequestException(String message) {
        super(message);
    }
}
