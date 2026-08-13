package com.glovishedge.document.extractor;

import com.glovishedge.document.exception.DocumentParsingException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * TXT/MD는 원문 그대로 읽는다 — Markdown을 HTML로 렌더링하지 않는다.
 * 기본 charset은 UTF-8이며, 디코딩 불가능한 바이트가 있으면 임의로 다른 charset을 순차 시도하지 않고
 * 실패로 처리한다(깨진 텍스트를 정상으로 위장하지 않기 위함).
 */
@Component
public class PlainTextExtractor implements DocumentTextExtractor {

    @Override
    public boolean supports(String extension) {
        return "txt".equals(extension) || "md".equals(extension);
    }

    @Override
    public String extract(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(content));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            throw new DocumentParsingException("UTF-8로 디코딩할 수 없는 텍스트 파일입니다", e);
        }
    }
}
