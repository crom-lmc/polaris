package com.laimengchao.sso.file.module.file.service.impl;

import com.laimengchao.sso.file.module.file.service.ThumbnailService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 图片缩略图：JDK ImageIO 原生实现，不引入第三方依赖。
 * <p>
 * 按需缩放（读取原图 → 等比缩放到指定最大边 → 输出 JPEG），失败时回退原图，
 * 保证缩略图端点永不因图片格式问题 500。透明通道统一铺白底避免 JPEG 变黑。
 */
@Slf4j
@Service
public class ThumbnailServiceImpl implements ThumbnailService {

    /** 输出为 JPEG 的格式名 */
    private static final String FORMAT = "jpg";

    /**
     * 等比缩放到最大边 maxEdge；非图片或处理失败时返回原字节。
     */
    public byte[] scale(byte[] src, int maxEdge) {
        if (src == null || src.length == 0 || maxEdge <= 0) {
            return src;
        }
        try {
            BufferedImage srcImg = ImageIO.read(new ByteArrayInputStream(src));
            if (srcImg == null) {
                return src; // 非位图（如 SVG/PDF），原样返回交给前端处理
            }
            int w = srcImg.getWidth();
            int h = srcImg.getHeight();
            if (w <= 0 || h <= 0) {
                return src;
            }
            int tw = w;
            int th = h;
            if (Math.max(w, h) > maxEdge) {
                double ratio = (double) maxEdge / Math.max(w, h);
                tw = Math.max(1, (int) Math.round(w * ratio));
                th = Math.max(1, (int) Math.round(h * ratio));
            }
            // 已经比目标小：仍重新编码可统一缩略图内容类型，但保留原图更省 CPU
            if (tw == w && th == h) {
                return src;
            }
            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, tw, th); // 透明像素铺白底
                g.drawImage(srcImg, 0, 0, tw, th, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, FORMAT, bos);
            return bos.toByteArray();
        } catch (IOException | RuntimeException e) {
            log.warn("缩略图生成失败，回退原图：{}", e.getMessage());
            return src;
        }
    }

    /** 是否为可做缩略图的位图 MIME。 */
    public static boolean isRasterImage(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase();
        return ct.startsWith("image/") && !ct.contains("svg");
    }
}
