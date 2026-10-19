package com.rag.assistant.module.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class DocumentParserService {

    public String parsePdf(InputStream inputStream) {
        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        Document document = parser.parse(inputStream);
        String text = document.text();
        log.debug("Parsed PDF: {} chars", text.length());
        return text;
    }

    public String parseMd(InputStream inputStream) {
        try {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Parsed MD: {} chars", text.length());
            return text;
        } catch (Exception e) {
            throw new RuntimeException("MD文件解析失败: " + e.getMessage());
        }
    }

    public String parseDoc(InputStream inputStream) {
        try {
            HWPFDocument document = new HWPFDocument(inputStream);
            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();
            log.debug("Parsed DOC: {} chars", text.length());
            return text;
        } catch (Exception e) {
            throw new RuntimeException("DOC文件解析失败: " + e.getMessage());
        }
    }

    public String parseByType(InputStream inputStream, String fileType) {
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("文件读取失败: " + e.getMessage());
        }

        try {
            return switch (fileType.toLowerCase()) {
                case "pdf" -> parsePdf(new ByteArrayInputStream(bytes));
                case "md" -> parseMd(new ByteArrayInputStream(bytes));
                case "doc" -> parseDoc(new ByteArrayInputStream(bytes));
                default -> throw new RuntimeException("不支持的文件类型: " + fileType);
            };
        } catch (Exception e) {
            log.warn("Primary parser failed for {} file, falling back to plain text: {}", fileType, e.getMessage());
            String text = new String(bytes, StandardCharsets.UTF_8);
            log.info("Fallback plain text extraction: {} chars", text.length());
            return text;
        }
    }
}
