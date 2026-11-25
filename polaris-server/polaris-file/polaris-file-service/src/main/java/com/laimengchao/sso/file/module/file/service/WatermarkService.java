package com.laimengchao.sso.file.module.file.service;

import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.entity.WatermarkConfig;
import com.laimengchao.sso.file.module.file.mapper.WatermarkConfigMapper;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 附件水印：按业务类型查配置，把用户/日期等变量注入模板，再用 JDK ImageIO + Graphics2D
 * 原生把文字平铺/居中叠加到位图字节流；不落原文件，失败回退原图（零新依赖）。
 *
 * <p>PDF 用 PDFBox、Word/Excel/PPT 用 POI，SVG 用原生字符串注入；均不修改原文件，
 * 任何失败都回退原字节，绝不因水印导致下载失败。老版 .doc/.xls/.ppt 与非文本格式不支持。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatermarkService {

    private final WatermarkConfigMapper configMapper;

    /**
     * 统一入口：按文件与生效环节，把水印烧录到字节流。
     * 身份由调用方显式传入，适配跨服务下载（无登录态）场景。无配置或不适用时原样返回。
     *
     * @param obj      文件元数据
     * @param bytes    原始字节
     * @param scene    VIEW/SHARE/DOWNLOAD
     * @param username 下载人用户名
     * @param userId   下载人账号 ID
     * @param clientIp 客户端 IP
     */
    public byte[] applyForDownload(FileObject obj, byte[] bytes, String scene,
                                   String username, String userId, String clientIp) {
        if (obj == null || bytes == null || bytes.length == 0) {
            return bytes;
        }
        WatermarkConfig cfg = config(obj.getBizType());
        String fileType = detectFileType(obj);
        if (fileType == null || !applies(cfg, scene, fileType)) {
            return bytes;
        }
        String text = renderedText(cfg, obj, clientIp, username, userId);
        return switch (fileType) {
            case "PDF" -> overlayPdf(bytes, cfg, text);
            case "SVG" -> overlaySvg(bytes, cfg, text);
            case "DOCX" -> overlayDocx(bytes, cfg, text);
            case "XLSX" -> overlayXlsx(bytes, cfg, text);
            case "PPTX" -> overlayPptx(bytes, cfg, text);
            default -> overlayImage(bytes, cfg, text);
        };
    }

    /** 按 MIME 与原文件名识别可烧录类型；IMAGE/SVG/PDF/DOCX/XLSX/PPTX，其余返回 null。 */
    public String detectFileType(FileObject obj) {
        if (obj == null) {
            return null;
        }
        String ct = obj.getContentType() == null ? "" : obj.getContentType().toLowerCase();
        String name = obj.getOriginalName() == null ? "" : obj.getOriginalName().toLowerCase();
        if (ThumbnailService.isRasterImage(ct) || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")
                || name.endsWith(".bmp") || name.endsWith(".ico")) {
            return "IMAGE";
        }
        if (ct.contains("svg") || name.endsWith(".svg")) {
            return "SVG";
        }
        if (ct.contains("pdf") || name.endsWith(".pdf")) {
            return "PDF";
        }
        if (ct.contains("wordprocessingml") || name.endsWith(".docx")) {
            return "DOCX";
        }
        if (ct.contains("spreadsheetml") || name.endsWith(".xlsx")) {
            return "XLSX";
        }
        if (ct.contains("presentationml") || name.endsWith(".pptx")) {
            return "PPTX";
        }
        return null;
    }

    /** 查某业务类型启用的水印配置；无则 null。带轻量缓存由调用方（每次请求查一次）即可，不引入缓存框架。 */
    public WatermarkConfig config(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            return null;
        }
        List<WatermarkConfig> list = configMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WatermarkConfig>()
                        .eq(WatermarkConfig::getBizType, bizType)
                        .eq(WatermarkConfig::getEnabled, true)
                        .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 该配置是否对指定文件类型与生效环节生效。scene: VIEW/SHARE/DOWNLOAD；type: IMAGE/SVG/PDF/DOCX/XLSX/PPTX。 */
    public boolean applies(WatermarkConfig cfg, String scene, String fileType) {
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
            return false;
        }
        if (fileType != null && !contains(cfg.getFileTypes(), fileType)) {
            return false;
        }
        return scene == null || contains(cfg.getScenes(), scene);
    }

    /** 把模板变量替换为真实值，返回可直接绘制的多行文本（身份取自当前登录上下文，用于站内预览/下载）。 */
    public String renderedText(WatermarkConfig cfg, FileObject obj, String clientIp) {
        String username = AccountContext.getUsername();
        String accountId = AccountContext.getAccountId() == null ? null : String.valueOf(AccountContext.getAccountId());
        return renderedText(cfg, obj, clientIp, username, accountId);
    }

    /**
     * 把模板变量替换为真实值，身份由调用方显式传入（跨服务下载时由业务服务传当前登录人，
     * 因为 file 服务的下载/内部端点不都带登录态）。
     */
    public String renderedText(WatermarkConfig cfg, FileObject obj, String clientIp,
                               String username, String accountId) {
        String tpl = cfg.getContent() == null ? "" : cfg.getContent();
        String date = LocalDate.now().toString();
        String ip = safe(clientIp, "");
        String fileName = obj == null || obj.getOriginalName() == null ? "" : obj.getOriginalName();
        String app = "北极星云";
        return tpl.replace("{username}", safe(username, "用户"))
                .replace("{userId}", accountId == null ? "" : accountId)
                .replace("{date}", date)
                .replace("{ip}", ip)
                .replace("{filename}", fileName)
                .replace("{app}", app)
                .replace("\\n", "\n");
    }

    /**
     * 把水印叠加到位图字节；非位图或失败时原样返回。
     *
     * @param src       原图字节
     * @param cfg       水印配置
     * @param text      已渲染的多行文本
     */
    public byte[] overlayImage(byte[] src, WatermarkConfig cfg, String text) {
        if (src == null || src.length == 0 || cfg == null || text == null || text.isBlank()) {
            return src;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(src));
            if (img == null) {
                return src; // 非位图（SVG/PDF），原样返回
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w <= 0 || h <= 0) {
                return src;
            }
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D base = out.createGraphics();
            try {
                base.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                base.drawImage(img, 0, 0, null);
            } finally {
                base.dispose();
            }

            Graphics2D g = out.createGraphics();
            try {
                int fontSize = clamp(cfg.getFontSize() == null ? 26 : cfg.getFontSize(), 12, 72);
                int opacity = clamp(cfg.getOpacity() == null ? 30 : cfg.getOpacity(), 5, 100);
                int rotation = cfg.getRotation() == null ? -24 : cfg.getRotation();
                String color = cfg.getColor() == null || cfg.getColor().isBlank() ? "#8B5CF6" : cfg.getColor();
                String layout = cfg.getLayout() == null ? "TILE" : cfg.getLayout().toUpperCase();
                int gap = clamp(cfg.getGap() == null ? 46 : cfg.getGap(), 18, 120);

                Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
                g.setFont(font);
                g.setColor(parseColor(color));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity / 100f));
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.rotate(Math.toRadians(rotation), w / 2.0, h / 2.0);

                String[] lines = text.split("\n");
                java.awt.FontMetrics fm = g.getFontMetrics();
                int lineH = fm.getHeight();
                int textW = 0;
                for (String line : lines) {
                    textW = Math.max(textW, fm.stringWidth(line));
                }

                if ("CENTER".equals(layout)) {
                    int x = (w - textW) / 2;
                    int y = h / 2 - (lineH * lines.length) / 2 + fm.getAscent();
                    drawLines(g, lines, x, y, lineH);
                } else {
                    // 平铺：以对角线为步长，覆盖旋转后的画布（放大画布避免旋转露白边）
                    int stepX = textW + gap;
                    int stepY = lineH * lines.length + gap;
                    if (stepX <= 0) stepX = textW + gap;
                    if (stepY <= 0) stepY = lineH * lines.length + gap;
                    int span = (int) Math.hypot(w, h) / 2 + Math.max(stepX, stepY);
                    for (int y = -span; y < h + span; y += stepY) {
                        for (int x = -span; x < w + span; x += stepX) {
                            drawLines(g, lines, x, y + fm.getAscent(), lineH);
                        }
                    }
                }
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream(src.length + 1024);
            ImageIO.write(out, "jpg", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("图片水印合成失败，回退原图：{}", e.getMessage());
            return src;
        }
    }

    /**
     * 把水印烧录到 PDF 字节：用内置黑体字体逐页叠加，支持平铺/居中、字号/颜色/透明度/旋转。
     * 失败或非 PDF 时原样返回，绝不因水印导致下载失败。
     */
    public byte[] overlayPdf(byte[] src, WatermarkConfig cfg, String text) {
        if (src == null || src.length == 0 || cfg == null || text == null || text.isBlank()) {
            return src;
        }
        org.apache.pdfbox.pdmodel.PDDocument doc = null;
        try {
            doc = org.apache.pdfbox.pdmodel.PDDocument.load(src);
            if (doc.isEncrypted()) {
                log.warn("PDF 已加密，跳过水印烧录");
                return src;
            }
            byte[] fontBytes = loadFont();
            if (fontBytes == null) {
                return src;
            }
            int fontSize = clamp(cfg.getFontSize() == null ? 26 : cfg.getFontSize(), 12, 72);
            int opacity = clamp(cfg.getOpacity() == null ? 30 : cfg.getOpacity(), 5, 100);
            int rotation = cfg.getRotation() == null ? -24 : cfg.getRotation();
            String color = cfg.getColor() == null || cfg.getColor().isBlank() ? "#8B5CF6" : cfg.getColor();
            String layout = cfg.getLayout() == null ? "TILE" : cfg.getLayout().toUpperCase();
            int gap = clamp(cfg.getGap() == null ? 46 : cfg.getGap(), 18, 120);
            String[] lines = text.split("\n");
            org.apache.pdfbox.pdmodel.font.PDType0Font font =
                    org.apache.pdfbox.pdmodel.font.PDType0Font.load(doc, new java.io.ByteArrayInputStream(fontBytes));

            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                org.apache.pdfbox.pdmodel.PDPage page = doc.getPage(p);
                org.apache.pdfbox.pdmodel.common.PDRectangle box = page.getMediaBox();
                float pw = box.getWidth();
                float ph = box.getHeight();
                org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(
                        doc, page, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true);
                try {
                    cs.setNonStrokingColor(parseColor(color));
                    cs.setStrokingColor(parseColor(color));
                    org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState gs =
                            new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
                    gs.setNonStrokingAlphaConstant(opacity / 100f);
                    gs.setStrokingAlphaConstant(opacity / 100f);
                    cs.setGraphicsStateParameters(gs);

                    if ("CENTER".equals(layout)) {
                        drawPdfLines(cs, font, lines, fontSize, pw / 2 - measurePdf(text, font, fontSize) / 2,
                                ph / 2, rotation, pw, ph);
                    } else {
                        float stepX = measurePdf(text, font, fontSize) + gap;
                        float stepY = fontSize * lines.length + gap;
                        float span = (float) Math.hypot(pw, ph) / 2 + Math.max(stepX, stepY);
                        int row = 0;
                        for (float y = -span; y < ph + span; y += stepY) {
                            float offsetX = (row++ % 2 == 0) ? 0 : stepX / 2;
                            for (float x = -span + offsetX; x < pw + span; x += stepX) {
                                drawPdfLines(cs, font, lines, fontSize, x, y, rotation, pw, ph);
                            }
                        }
                    }
                } finally {
                    cs.close();
                }
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(src.length + 4096);
            doc.save(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("PDF 水印烧录失败，回退原文件：{}", e.getMessage());
            return src;
        } finally {
            if (doc != null) {
                try { doc.close(); } catch (Exception ignored) { }
            }
        }
    }

    /** 旋转绘制多行 PDF 文本（围绕页面中心旋转，坐标为旋转前的绝对位置）。 */
    private void drawPdfLines(org.apache.pdfbox.pdmodel.PDPageContentStream cs,
                              org.apache.pdfbox.pdmodel.font.PDType0Font font,
                              String[] lines, float fontSize, float x, float y,
                              int rotation, float pw, float ph) throws Exception {
        double rad = Math.toRadians(rotation);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float cx = pw / 2;
        float cy = ph / 2;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setTextMatrix(cos, sin, -sin, cos,
                cx + (x - cx) * cos - (y - cy) * sin,
                cy + (x - cx) * sin + (y - cy) * cos);
        cs.showText(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            cs.newLineAtOffset(0, -fontSize * 1.4f);
            cs.showText(lines[i]);
        }
        cs.endText();
    }

    private float measurePdf(String text, org.apache.pdfbox.pdmodel.font.PDType0Font font, float fontSize) throws Exception {
        String first = text.split("\n")[0];
        return font.getStringWidth(first) / 1000f * fontSize;
    }

    /** 加载内置中文字体（随 jar 打包），全局缓存一次。 */
    private static volatile byte[] fontCache;
    private byte[] loadFont() {
        if (fontCache != null) {
            return fontCache;
        }
        synchronized (WatermarkService.class) {
            if (fontCache != null) {
                return fontCache;
            }
            try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("fonts/simhei.ttf")) {
                if (in == null) {
                    log.warn("未找到内置水印字体 fonts/simhei.ttf，跳过 PDF 水印");
                    return null;
                }
                fontCache = in.readAllBytes();
                return fontCache;
            } catch (Exception e) {
                log.warn("加载水印字体失败：{}", e.getMessage());
                return null;
            }
        }
    }

    private void drawLines(Graphics2D g, String[] lines, int x, int y, int lineH) {
        int cy = y;
        for (String line : lines) {
            g.drawString(line, x, cy);
            cy += lineH;
        }
    }

    /** SVG：在根节点内注入 pattern/text，不依赖第三方库；失败回退原文件。 */
    public byte[] overlaySvg(byte[] src, WatermarkConfig cfg, String text) {
        if (src == null || src.length == 0 || cfg == null || text == null || text.isBlank()) {
            return src;
        }
        try {
            String svg = new String(src, java.nio.charset.StandardCharsets.UTF_8);
            int end = svg.lastIndexOf("</svg>");
            if (end < 0) {
                return src;
            }
            int fontSize = clamp(cfg.getFontSize() == null ? 26 : cfg.getFontSize(), 12, 72);
            int opacity = clamp(cfg.getOpacity() == null ? 30 : cfg.getOpacity(), 5, 100);
            int rotation = cfg.getRotation() == null ? -24 : cfg.getRotation();
            String color = cfg.getColor() == null || cfg.getColor().isBlank() ? "#8B5CF6" : cfg.getColor();
            String layout = cfg.getLayout() == null ? "TILE" : cfg.getLayout().toUpperCase();
            int gap = clamp(cfg.getGap() == null ? 46 : cfg.getGap(), 18, 120);
            String[] lines = text.split("\n");
            StringBuilder tspans = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                tspans.append("<tspan x=\"0\"");
                if (i > 0) {
                    tspans.append(" dy=\"").append((int) (fontSize * 1.4)).append("\"");
                }
                tspans.append('>').append(escapeXml(lines[i])).append("</tspan>");
            }
            String style = "fill=\"" + escapeXml(color) + "\" font-family=\"sans-serif\" font-size=\""
                    + fontSize + "\" font-weight=\"bold\"";
            String alpha = String.format(java.util.Locale.ROOT, "%.2f", opacity / 100f);
            String frag;
            if ("CENTER".equals(layout)) {
                frag = "<g transform=\"translate(50% 50%) rotate(" + rotation + ")\" opacity=\"" + alpha
                        + "\"><text " + style + " text-anchor=\"middle\">" + tspans + "</text></g>";
            } else {
                int approx = 0;
                for (String line : lines) {
                    approx = Math.max(approx, (int) (line.length() * fontSize * 0.8));
                }
                String id = "polaris-wm-" + Math.abs(text.hashCode());
                frag = "<defs><pattern id=\"" + id + "\" width=\"" + (approx + gap) + "\" height=\""
                        + (fontSize * lines.length + gap) + "\" patternUnits=\"userSpaceOnUse\" patternTransform=\"rotate("
                        + rotation + ")\"><text " + style + " x=\"0\" y=\"" + fontSize + "\">" + tspans
                        + "</text></pattern></defs><rect x=\"0\" y=\"0\" width=\"100%\" height=\"100%\" fill=\"url(#"
                        + id + ")\" opacity=\"" + alpha + "\" pointer-events=\"none\"/>";
            }
            String out = svg.substring(0, end) + frag + svg.substring(end);
            return out.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("SVG 水印合成失败，回退原文件：{}", e.getMessage());
            return src;
        }
    }

    /** DOCX：页眉写入 VML 斜向水印；VML 失败时回退为普通页眉文字。 */
    public byte[] overlayDocx(byte[] src, WatermarkConfig cfg, String text) {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = null;
        try {
            doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(src));
            org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy policy = doc.getHeaderFooterPolicy();
            if (policy == null) {
                policy = doc.createHeaderFooterPolicy();
            }
            org.apache.poi.xwpf.usermodel.XWPFHeader header = policy.getDefaultHeader();
            if (header == null) {
                header = policy.createHeader(org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT);
            }
            org.apache.poi.xwpf.usermodel.XWPFParagraph p = header.createParagraph();
            int fontSize = clamp(cfg.getFontSize() == null ? 26 : cfg.getFontSize(), 12, 72);
            int opacity = clamp(cfg.getOpacity() == null ? 30 : cfg.getOpacity(), 5, 100);
            int rotation = cfg.getRotation() == null ? -24 : cfg.getRotation();
            String color = cfg.getColor() == null || cfg.getColor().isBlank() ? "#8B5CF6" : cfg.getColor();
            String alpha = String.format(java.util.Locale.ROOT, "%.2f", opacity / 100f);
            try {
                String vml = "<w:pict xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                        + "xmlns:v=\"urn:schemas-microsoft-com:vml\"><v:shape id=\"PolarisWaterMark\" "
                        + "type=\"#_x0000_t136\" style=\"position:absolute;margin-left:0;margin-top:0;"
                        + "width:420pt;height:90pt;rotation:" + rotation
                        + ";z-index:-251658752;mso-position-horizontal:center;"
                        + "mso-position-horizontal-relative:margin;mso-position-vertical:center;"
                        + "mso-position-vertical-relative:margin\" fillcolor=\"" + escapeXml(color)
                        + "\" stroked=\"f\"><v:fill opacity=\"" + alpha
                        + "\"/><v:textpath style=\"font-family:SimHei;font-size:1pt\" string=\""
                        + escapeXml(text.replace("\n", " ")) + "\"/></v:shape></w:pict>";
                org.apache.xmlbeans.XmlObject xml = org.apache.xmlbeans.XmlObject.Factory.parse(vml);
                p.getCTP().set(xml);
            } catch (Exception inner) {
                log.warn("DOCX VML 水印写入失败，回退普通页眉文字：{}", inner.getMessage());
                org.apache.poi.xwpf.usermodel.XWPFRun run = p.createRun();
                run.setText(text.replace("\n", " "));
                run.setFontSize(fontSize);
                run.setColor(color.replace("#", ""));
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(src.length + 4096);
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("DOCX 水印烧录失败，回退原文件：{}", e.getMessage());
            return src;
        } finally {
            if (doc != null) {
                try { doc.close(); } catch (Exception ignored) { }
            }
        }
    }

    /** XLSX：把水印写入每页打印页眉（POI 对 Excel 的标准做法，打印/导出 PDF 时可见）。 */
    public byte[] overlayXlsx(byte[] src, WatermarkConfig cfg, String text) {
        org.apache.poi.xssf.usermodel.XSSFWorkbook wb = null;
        try {
            wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(src));
            int fontSize = clamp(cfg.getFontSize() == null ? 26 : cfg.getFontSize(), 12, 72);
            String color = cfg.getColor() == null || cfg.getColor().isBlank() ? "#8B5CF6" : cfg.getColor();
            String hex = color.startsWith("#") ? color.substring(1) : color;
            String header = "&K" + hex + "&\"SimHei,Bold\"&" + fontSize + text.replace("\n", " ");
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                wb.getSheetAt(i).getHeader().setCenter(header);
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(src.length + 4096);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("XLSX 水印烧录失败，回退原文件：{}", e.getMessage());
            return src;
        } finally {
            if (wb != null) {
                try { wb.close(); } catch (Exception ignored) { }
            }
        }
    }

    /** PPTX：逐页插入文本框水印，支持平铺/居中与旋转；颜色按透明度向白色混合近似。 */
    public byte[] overlayPptx(byte[] src, WatermarkConfig cfg, String text) {
        org.apache.poi.xslf.usermodel.XMLSlideShow ppt = null;
        try {
            ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(new java.io.ByteArrayInputStream(src));
            java.awt.Dimension size = ppt.getPageSize();
            int fontSize = clamp(cfg.getFontSize() == null ? 26 : cfg.getFontSize(), 12, 72);
            int opacity = clamp(cfg.getOpacity() == null ? 30 : cfg.getOpacity(), 5, 100);
            int rotation = cfg.getRotation() == null ? -24 : cfg.getRotation();
            int gap = clamp(cfg.getGap() == null ? 46 : cfg.getGap(), 18, 120);
            String color = cfg.getColor() == null || cfg.getColor().isBlank() ? "#8B5CF6" : cfg.getColor();
            String layout = cfg.getLayout() == null ? "TILE" : cfg.getLayout().toUpperCase();
            String shown = text.replace("\n", "  ");
            for (org.apache.poi.xslf.usermodel.XSLFSlide slide : ppt.getSlides()) {
                if ("CENTER".equals(layout)) {
                    addPptWatermark(slide, size, shown, rotation, fontSize, color, opacity, 0, 0, true);
                } else {
                    int stepX = Math.max(120, (int) (shown.length() * fontSize * 0.7) + gap);
                    int stepY = fontSize * 2 + gap;
                    int count = 0;
                    for (int y = -stepY; y < size.height + stepY && count < 80; y += stepY) {
                        for (int x = -stepX; x < size.width + stepX && count < 80; x += stepX) {
                            addPptWatermark(slide, size, shown, rotation, fontSize, color, opacity, x, y, false);
                            count++;
                        }
                    }
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(src.length + 4096);
            ppt.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("PPTX 水印烧录失败，回退原文件：{}", e.getMessage());
            return src;
        } finally {
            if (ppt != null) {
                try { ppt.close(); } catch (Exception ignored) { }
            }
        }
    }

    private void addPptWatermark(org.apache.poi.xslf.usermodel.XSLFSlide slide, java.awt.Dimension size,
                                 String text, int rotation, int fontSize, String color, int opacity,
                                 int x, int y, boolean center) {
        int w = Math.min(size.width, Math.max(80, (int) (text.length() * fontSize * 0.7)));
        int h = Math.max(30, fontSize * 2);
        int px = center ? (size.width - w) / 2 : x;
        int py = center ? (size.height - h) / 2 : y;
        org.apache.poi.xslf.usermodel.XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new java.awt.Rectangle(px, py, w, h));
        box.setRotation(rotation);
        org.apache.poi.xslf.usermodel.XSLFTextParagraph para = box.getTextParagraphs().isEmpty()
                ? box.addNewTextParagraph() : box.getTextParagraphs().get(0);
        if (center) {
            para.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        }
        org.apache.poi.xslf.usermodel.XSLFTextRun run = para.addNewTextRun();
        run.setText(text);
        run.setFontSize((double) fontSize);
        run.setFontColor(blendColor(color, opacity));
    }

    private static String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static Color blendColor(String hex, int opacity) {
        Color c = parseColor(hex);
        float keep = opacity / 100f;
        return new Color(
                Math.round(c.getRed() + (255 - c.getRed()) * (1 - keep)),
                Math.round(c.getGreen() + (255 - c.getGreen()) * (1 - keep)),
                Math.round(c.getBlue() + (255 - c.getBlue()) * (1 - keep)));
    }

    private static Color parseColor(String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 3) {
                h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            }
            return new Color(Integer.parseInt(h, 16));
        } catch (Exception e) {
            return new Color(139, 92, 246);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean contains(String csv, String token) {
        if (csv == null || csv.isBlank() || token == null) {
            return false;
        }
        Set<String> set = new HashSet<>(Arrays.asList(csv.split(",")));
        return set.contains(token);
    }

    private static String safe(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
