package com.glovishedge.document.extractor;

import com.glovishedge.document.exception.DocumentParsingException;

/**
 * 확장자별 텍스트 추출 전략. Service는 확장자로 알맞은 구현체를 찾아 위임하고,
 * 형식별 파싱 세부사항(매직바이트 검증 · 라이브러리 호출 · 손상 파일 처리)은 여기에 둔다.
 */
public interface DocumentTextExtractor {

    boolean supports(String extension);

    String extract(byte[] content) throws DocumentParsingException;
}
