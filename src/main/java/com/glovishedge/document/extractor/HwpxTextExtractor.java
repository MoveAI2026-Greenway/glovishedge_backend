package com.glovishedge.document.extractor;

import com.glovishedge.document.exception.DocumentParsingException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * HWPX(ZIP/XML, OWPML 기반)에서 본문 section을 찾아 텍스트를 문서 순서대로 추출한다.
 * 구형 바이너리 .hwp용 라이브러리는 쓰지 않는다 — 표준 ZIP/XML API로 직접 읽는다.
 *
 * <p>보안: ZIP entry 경로 검증(Zip Slip 방지), 총 압축해제 크기 상한(zip bomb 방지),
 * XML 파서의 DOCTYPE/외부 엔티티 비활성화(XXE 방지)를 적용한다.
 */
@Component
public class HwpxTextExtractor implements DocumentTextExtractor {

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final Pattern SECTION_ENTRY = Pattern.compile(
            "(?i)^Contents/section(\\d+)\\.xml$");
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 50L * 1024 * 1024; // zip bomb 상한

    @Override
    public boolean supports(String extension) {
        return "hwpx".equals(extension);
    }

    @Override
    public String extract(byte[] content) {
        if (!hasZipMagicBytes(content)) {
            throw new DocumentParsingException("HWPX 파일이 아니거나 손상되었습니다");
        }

        TreeMap<Integer, byte[]> sections = new TreeMap<>();
        long totalUncompressed = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                validateEntryName(name);

                Matcher matcher = SECTION_ENTRY.matcher(name);
                if (matcher.matches()) {
                    byte[] entryBytes = readBounded(zip, MAX_TOTAL_UNCOMPRESSED_BYTES - totalUncompressed);
                    totalUncompressed += entryBytes.length;
                    sections.put(Integer.parseInt(matcher.group(1)), entryBytes);
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new DocumentParsingException("HWPX(ZIP) 파일을 여는 데 실패했습니다", e);
        }

        if (sections.isEmpty()) {
            throw new DocumentParsingException("HWPX 문서 본문(section) 구조를 찾을 수 없습니다");
        }

        StringBuilder result = new StringBuilder();
        for (byte[] sectionXml : sections.values()) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(extractSectionText(sectionXml));
        }
        return result.toString();
    }

    private void validateEntryName(String name) {
        if (name.startsWith("/") || name.contains("..") || name.contains("\\")) {
            throw new DocumentParsingException("HWPX 파일에 허용되지 않는 항목 경로가 있습니다: " + name);
        }
    }

    private byte[] readBounded(InputStream in, long remainingBudget) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long readTotal = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            readTotal += n;
            if (readTotal > remainingBudget) {
                throw new DocumentParsingException("HWPX 압축 해제 결과가 허용 크기를 초과했습니다");
            }
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    private String extractSectionText(byte[] sectionXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document dom = builder.parse(new ByteArrayInputStream(sectionXml));

            List<String> paragraphs = new ArrayList<>();
            collectParagraphText(dom.getDocumentElement(), paragraphs);
            return String.join("\n", paragraphs);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new DocumentParsingException("HWPX section XML 파싱에 실패했습니다", e);
        }
    }

    /**
     * local-name이 "p"(hp:p, 문단)인 요소마다 하위 텍스트 노드를 모아 한 문단으로 합친다.
     */
    private void collectParagraphText(Node node, List<String> paragraphs) {
        if (node == null) {
            return;
        }
        if (node.getNodeType() == Node.ELEMENT_NODE && "p".equals(localName(node))) {
            String text = collectText(node).trim();
            if (!text.isEmpty()) {
                paragraphs.add(text);
            }
            return;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectParagraphText(children.item(i), paragraphs);
        }
    }

    private String collectText(Node node) {
        StringBuilder sb = new StringBuilder();
        appendText(node, sb);
        return sb.toString();
    }

    private void appendText(Node node, StringBuilder sb) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            sb.append(node.getNodeValue());
            return;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            appendText(children.item(i), sb);
        }
    }

    private String localName(Node node) {
        String local = node.getLocalName();
        if (local != null) {
            return local;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
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
