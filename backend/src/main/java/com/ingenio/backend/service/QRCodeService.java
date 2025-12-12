package com.ingenio.backend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成服务
 *
 * 功能：
 * - 生成下载链接二维码
 * - 支持自定义尺寸和容错级别
 * - 返回PNG格式图片字节流
 *
 * 使用场景：
 * - 应用下载二维码
 * - 分享链接二维码
 * - 营销推广二维码
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class QRCodeService {

    /**
     * 默认二维码尺寸（像素）
     */
    private static final int DEFAULT_SIZE = 300;

    /**
     * 默认容错级别
     */
    private static final ErrorCorrectionLevel DEFAULT_ERROR_CORRECTION = ErrorCorrectionLevel.M;

    /**
     * 生成二维码（默认尺寸和容错级别）
     *
     * @param content 二维码内容（通常是URL）
     * @return PNG格式图片字节数组
     */
    public byte[] generateQRCode(String content) {
        return generateQRCode(content, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    /**
     * 生成二维码（自定义尺寸）
     *
     * @param content 二维码内容（通常是URL）
     * @param width   宽度（像素）
     * @param height  高度（像素）
     * @return PNG格式图片字节数组
     */
    public byte[] generateQRCode(String content, int width, int height) {
        return generateQRCode(content, width, height, DEFAULT_ERROR_CORRECTION);
    }

    /**
     * 生成二维码（完整参数）
     *
     * @param content          二维码内容（通常是URL）
     * @param width            宽度（像素）
     * @param height           高度（像素）
     * @param errorCorrection  容错级别（L=7%, M=15%, Q=25%, H=30%）
     * @return PNG格式图片字节数组
     * @throws RuntimeException 当生成失败时抛出
     */
    public byte[] generateQRCode(String content, int width, int height, ErrorCorrectionLevel errorCorrection) {
        log.info("生成二维码 - content: {}, size: {}x{}, errorCorrection: {}",
                content, width, height, errorCorrection);

        try {
            // 配置二维码参数
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, errorCorrection);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1); // 边距

            // 生成二维码矩阵
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            // 转换为PNG图片字节流
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] qrCodeBytes = outputStream.toByteArray();

            log.info("二维码生成成功 - size: {} bytes", qrCodeBytes.length);
            return qrCodeBytes;

        } catch (WriterException e) {
            log.error("二维码编码失败 - content: {}", content, e);
            throw new RuntimeException("二维码编码失败: " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("二维码图片生成失败 - content: {}", content, e);
            throw new RuntimeException("二维码图片生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成下载链接二维码
     *
     * 专门用于应用下载，使用较大尺寸和较高容错级别
     *
     * @param downloadUrl 下载链接
     * @return PNG格式图片字节数组
     */
    public byte[] generateDownloadQRCode(String downloadUrl) {
        log.info("生成下载二维码 - url: {}", downloadUrl);
        // 下载二维码使用更大尺寸和更高容错级别
        return generateQRCode(downloadUrl, 400, 400, ErrorCorrectionLevel.H);
    }

    /**
     * 生成分享链接二维码
     *
     * 专门用于分享，使用中等尺寸和中等容错级别
     *
     * @param shareUrl 分享链接
     * @return PNG格式图片字节数组
     */
    public byte[] generateShareQRCode(String shareUrl) {
        log.info("生成分享二维码 - url: {}", shareUrl);
        return generateQRCode(shareUrl, 300, 300, ErrorCorrectionLevel.M);
    }
}
