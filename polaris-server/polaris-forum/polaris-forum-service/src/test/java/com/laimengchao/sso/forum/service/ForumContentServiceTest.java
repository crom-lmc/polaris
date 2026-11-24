package com.laimengchao.sso.forum.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForumContentServiceTest {

    private final ForumContentService service = new ForumContentService();

    @Test
    void keepsSupportedFormatting() {
        String cleaned = service.sanitize("<h2>标题</h2><p><strong>加粗</strong><em>斜体</em><u>下划线</u><s>删除线</s></p>"
                + "<ul><li>无序</li></ul><ol><li>有序</li></ol><blockquote>引用</blockquote>");
        assertTrue(cleaned.contains("<h2>标题</h2>"));
        assertTrue(cleaned.contains("<strong>加粗</strong>"));
        assertTrue(cleaned.contains("<em>斜体</em>"));
        assertTrue(cleaned.contains("<u>下划线</u>"));
        assertTrue(cleaned.contains("<s>删除线</s>"));
        assertTrue(cleaned.contains("<ul><li>无序</li></ul>"));
        assertTrue(cleaned.contains("<ol><li>有序</li></ol>"));
        assertTrue(cleaned.contains("<blockquote>引用</blockquote>"));
    }

    @Test
    void removesUnsafeTagsAndAttributes() {
        String cleaned = service.sanitize("<p class=\"x\" style=\"color:red\" onclick=\"alert(1)\">安全"
                + "<script>alert(1)</script><img src=x onerror=alert(1)>"
                + "<a href=\"javascript:alert(1)\">链接</a><span>文本</span></p>");
        assertFalse(cleaned.contains("script"));
        assertFalse(cleaned.contains("onclick"));
        assertFalse(cleaned.contains("onerror"));
        assertFalse(cleaned.contains("style"));
        assertFalse(cleaned.contains("class"));
        assertFalse(cleaned.contains("<img"));
        assertFalse(cleaned.contains("<a"));
        assertTrue(cleaned.contains("安全"));
        assertTrue(cleaned.contains("链接"));
        assertTrue(cleaned.contains("文本"));
    }

    @Test
    void keepsInlineImageWithRelativeSrc() {
        String cleaned = service.sanitize("<p><img src=\"/api/forum/media/file/383314035510218752.jpg\" alt=\"插图\"></p><p>正文</p>");
        assertTrue(cleaned.contains("src=\"/api/forum/media/file/383314035510218752.jpg\""));
        assertTrue(cleaned.contains("alt=\"插图\""));
        assertTrue(cleaned.contains("正文"));
        // 危险协议与站外相对路径的插图仍要拦截
        assertFalse(service.sanitize("<img src=\"javascript:alert(1)\" alt=\"x\">").contains("<img"));
        assertFalse(service.sanitize("<img src=\"/etc/passwd\" alt=\"x\">").contains("<img"));
        assertFalse(service.sanitize("<img src=\"http://evil.example/a.png\" alt=\"x\">").contains("<img"));
    }

    @Test
    void extractsPlainTextForValidationAndSensitiveWords() {
        assertEquals("赌博", service.plainText("<p>赌<strong></strong>博</p>"));
        assertEquals("第一段 第二段", service.plainText("<p>第一段</p><p>第二段</p>"));
        assertEquals("", service.plainText(service.sanitize("<p><br></p>")));
    }

    @Test
    void keepsLegacyPlainText() {
        String cleaned = service.sanitize("第一行\n第二行");
        assertEquals("第一行\n第二行", cleaned);
        assertEquals("第一行 第二行", service.plainText(cleaned));
    }
}
