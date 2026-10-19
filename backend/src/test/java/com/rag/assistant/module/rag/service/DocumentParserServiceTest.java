package com.rag.assistant.module.rag.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档解析：PDF / Markdown 两条入口都要能把字节流还原成可入库的纯文本。
 */
class DocumentParserServiceTest {

    private final DocumentParserService parser = new DocumentParserService();

    @Test
    @DisplayName("Markdown 按 UTF-8 解析，中文与 Markdown 标记原样保留")
    void parsesMarkdownAsUtf8() {
        String content = "# 标题\n\n知识库保存的是「文档解析 -> 分块 -> 向量化」链路。";

        assertEquals(content, parser.parseMd(stream(content)));
    }

    @Test
    @DisplayName("未知类型走纯文本兜底，而不是抛异常中断入库")
    void unknownTypeFallsBackToPlainText() {
        String content = "没有扩展名的普通文本内容";

        assertEquals(content, parser.parseByType(stream(content), "txt"));
    }

    @Test
    @DisplayName("PDF 用 PDFBox 抽出正文文本")
    void parsesPdfText() throws Exception {
        byte[] pdf = samplePdf("RAG pipeline smoke test");

        String text = parser.parsePdf(new ByteArrayInputStream(pdf));

        assertTrue(text.contains("RAG pipeline smoke test"), "PDF 正文未解析出来：" + text);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] samplePdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDFont font = PDType1Font.HELVETICA;
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(60, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}