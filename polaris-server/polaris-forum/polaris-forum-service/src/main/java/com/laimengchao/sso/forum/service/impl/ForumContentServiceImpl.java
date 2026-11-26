package com.laimengchao.sso.forum.service.impl;

import com.laimengchao.sso.forum.service.ForumContentService;

import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

@Service
public class ForumContentServiceImpl implements ForumContentService {

    /**
     * 不能用 Safelist.basicWithImages()：它给 img[src] 注册了 http/https 协议白名单，
     * 而 preserveRelativeLinks(true) 只是阻止把相对地址绝对化，并不会放行相对路径，
     * 会把统一文件服务的正文插图 src（/api/forum/media/file/...）剥掉。
     * 这里手动放行标签与属性，再由 sanitize() 统一校验 URL 协议。
     */
    private static final Safelist ALLOWED = new Safelist()
            .addTags(
                    "p", "br", "strong", "b", "em", "i", "u", "s", "del",
                    "h2", "h3", "ul", "ol", "li", "blockquote", "img")
            .addAttributes("img", "src", "alt")
            .preserveRelativeLinks(true);
    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings().prettyPrint(false);

    public String sanitize(String value) {
        String cleaned = Jsoup.clean(value == null ? "" : value, "", ALLOWED, OUTPUT_SETTINGS);
        Document doc = Jsoup.parseBodyFragment(cleaned);
        doc.outputSettings(OUTPUT_SETTINGS); // 与 clean 一致，避免二次序列化插入换行/空格
        // 与前端 utils/html.ts 的 ALLOWED_URI_REGEXP 对齐：只放行本站插图相对路径、https 与图片 data URI。
        // 无有效 src 的 img 直接移除，避免渲染成一张只有 alt 文本的破图。
        for (Element img : doc.select("img")) {
            if (!isSafeUrl(img.attr("src"))) {
                img.remove();
            }
        }
        return doc.body().html().trim();
    }

    private static boolean isSafeUrl(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("/api/forum/media/file/")
                || lower.startsWith("https://")
                || lower.startsWith("data:image/");
    }

    public String plainText(String html) {
        return Jsoup.parse(html == null ? "" : html).text().trim();
    }
}
