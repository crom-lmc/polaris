package com.laimengchao.sso.file.module.file.service;

import com.laimengchao.sso.file.module.file.entity.FileObject;
import com.laimengchao.sso.file.module.file.entity.WatermarkConfig;

public interface WatermarkService {

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
    byte[] applyForDownload(FileObject obj, byte[] bytes, String scene, String username, String userId, String clientIp);

    /** 按 MIME 与原文件名识别可烧录类型；IMAGE/SVG/PDF/DOCX/XLSX/PPTX，其余返回 null。 */
    String detectFileType(FileObject obj);

    /** 查某业务类型启用的水印配置；无则 null。带轻量缓存由调用方（每次请求查一次）即可，不引入缓存框架。 */
    WatermarkConfig config(String bizType);

    /** 该配置是否对指定文件类型与生效环节生效。scene: VIEW/SHARE/DOWNLOAD；type: IMAGE/SVG/PDF/DOCX/XLSX/PPTX。 */
    boolean applies(WatermarkConfig cfg, String scene, String fileType);

    /** 把模板变量替换为真实值，返回可直接绘制的多行文本（身份取自当前登录上下文，用于站内预览/下载）。 */
    String renderedText(WatermarkConfig cfg, FileObject obj, String clientIp);

    /**
     * 把模板变量替换为真实值，身份由调用方显式传入（跨服务下载时由业务服务传当前登录人，
     * 因为 file 服务的下载/内部端点不都带登录态）。
     */
    String renderedText(WatermarkConfig cfg, FileObject obj, String clientIp, String username, String accountId);

    /**
     * 把水印叠加到位图字节；非位图或失败时原样返回。
     *
     * @param src       原图字节
     * @param cfg       水印配置
     * @param text      已渲染的多行文本
     */
    byte[] overlayImage(byte[] src, WatermarkConfig cfg, String text);

    /**
     * 把水印烧录到 PDF 字节：用内置黑体字体逐页叠加，支持平铺/居中、字号/颜色/透明度/旋转。
     * 失败或非 PDF 时原样返回，绝不因水印导致下载失败。
     */
    byte[] overlayPdf(byte[] src, WatermarkConfig cfg, String text);

    /** SVG：在根节点内注入 pattern/text，不依赖第三方库；失败回退原文件。 */
    byte[] overlaySvg(byte[] src, WatermarkConfig cfg, String text);

    /** DOCX：页眉写入 VML 斜向水印；VML 失败时回退为普通页眉文字。 */
    byte[] overlayDocx(byte[] src, WatermarkConfig cfg, String text);

    /** XLSX：把水印写入每页打印页眉（POI 对 Excel 的标准做法，打印/导出 PDF 时可见）。 */
    byte[] overlayXlsx(byte[] src, WatermarkConfig cfg, String text);

    /** PPTX：逐页插入文本框水印，支持平铺/居中与旋转；颜色按透明度向白色混合近似。 */
    byte[] overlayPptx(byte[] src, WatermarkConfig cfg, String text);
}
