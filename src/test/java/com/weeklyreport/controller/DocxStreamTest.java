package com.weeklyreport.controller;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 docx 可直接写入任意 OutputStream（与 WeeklyReportController 中
 * StreamingResponseBody 的 document.write(outputStream) 一致），产出合法 docx。
 */
public class DocxStreamTest {

    @Test
    void writeToOutputStream_producesValidDocx() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("hello");
            doc.write(bos); // 等价于 StreamingResponseBody 中的写出方式
        }
        byte[] bytes = bos.toByteArray();
        assertTrue(bytes.length > 0, "docx 内容为空");
        // docx 本质是 ZIP 包，以 PK(0x50,0x4B) 开头
        assertEquals('P', (char) bytes[0]);
        assertEquals('K', (char) bytes[1]);
    }
}
