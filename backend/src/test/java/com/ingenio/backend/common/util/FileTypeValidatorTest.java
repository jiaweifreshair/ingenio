package com.ingenio.backend.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTypeValidator单元测试
 *
 * 测试覆盖范围:
 * - 图片类型验证（MIME类型和扩展名）
 * - 音频类型验证（MIME类型和扩展名）
 * - 文件大小验证
 * - 边界条件测试
 * - 异常情况处理
 *
 * @author Ingenio Team
 * @since 2025-11-10
 */
@DisplayName("文件类型验证器单元测试")
class FileTypeValidatorTest {

    private FileTypeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileTypeValidator();
    }

    // ==================== 图片类型验证测试 ====================

    @Test
    @DisplayName("验证合法的JPEG图片")
    void testValidJpegImage() {
        assertTrue(validator.isValidImage("image/jpeg", "photo.jpg"));
        assertTrue(validator.isValidImage("image/jpg", "photo.jpg"));
    }

    @Test
    @DisplayName("验证合法的PNG图片")
    void testValidPngImage() {
        assertTrue(validator.isValidImage("image/png", "screenshot.png"));
    }

    @Test
    @DisplayName("验证合法的GIF图片")
    void testValidGifImage() {
        assertTrue(validator.isValidImage("image/gif", "animation.gif"));
    }

    @Test
    @DisplayName("验证合法的WebP图片")
    void testValidWebpImage() {
        assertTrue(validator.isValidImage("image/webp", "modern.webp"));
    }

    @Test
    @DisplayName("验证合法的SVG图片")
    void testValidSvgImage() {
        assertTrue(validator.isValidImage("image/svg+xml", "icon.svg"));
    }

    @ParameterizedTest
    @CsvSource({
        "image/jpeg, photo.jpg",
        "image/png, screenshot.png",
        "image/gif, animation.gif",
        "image/webp, modern.webp",
        "image/bmp, bitmap.bmp",
        "image/svg+xml, icon.svg"
    })
    @DisplayName("参数化测试：验证所有支持的图片类型")
    void testAllSupportedImageTypes(String contentType, String filename) {
        assertTrue(validator.isValidImage(contentType, filename));
    }

    @Test
    @DisplayName("验证不合法的图片MIME类型")
    void testInvalidImageMimeType() {
        assertFalse(validator.isValidImage("application/pdf", "document.pdf"));
        assertFalse(validator.isValidImage("text/plain", "text.txt"));
        assertFalse(validator.isValidImage("video/mp4", "video.mp4"));
    }

    @Test
    @DisplayName("验证MIME类型与扩展名不匹配的图片")
    void testMismatchedImageTypeAndExtension() {
        // MIME类型正确，扩展名错误
        assertFalse(validator.isValidImage("image/jpeg", "photo.txt"));
        assertFalse(validator.isValidImage("image/png", "screenshot.pdf"));
    }

    @Test
    @DisplayName("验证图片类型的大小写敏感性")
    void testImageTypeCaseInsensitive() {
        // MIME类型大小写不敏感
        assertTrue(validator.isValidImage("IMAGE/JPEG", "photo.jpg"));
        assertTrue(validator.isValidImage("Image/Png", "screenshot.png"));

        // 文件扩展名大小写不敏感
        assertTrue(validator.isValidImage("image/jpeg", "photo.JPG"));
        assertTrue(validator.isValidImage("image/png", "screenshot.PNG"));
    }

    @Test
    @DisplayName("验证图片类型为null的情况")
    void testImageValidationWithNullInputs() {
        assertFalse(validator.isValidImage(null, "photo.jpg"));
        assertFalse(validator.isValidImage("image/jpeg", null));
        assertFalse(validator.isValidImage(null, null));
    }

    // ==================== 音频类型验证测试 ====================

    @Test
    @DisplayName("验证合法的MP3音频")
    void testValidMp3Audio() {
        assertTrue(validator.isValidAudio("audio/mpeg", "song.mp3"));
        assertTrue(validator.isValidAudio("audio/mp3", "song.mp3"));
    }

    @Test
    @DisplayName("验证合法的WAV音频")
    void testValidWavAudio() {
        assertTrue(validator.isValidAudio("audio/wav", "recording.wav"));
        assertTrue(validator.isValidAudio("audio/wave", "recording.wav"));
        assertTrue(validator.isValidAudio("audio/x-wav", "recording.wav"));
    }

    @Test
    @DisplayName("验证合法的M4A音频")
    void testValidM4aAudio() {
        assertTrue(validator.isValidAudio("audio/mp4", "voice.m4a"));
        assertTrue(validator.isValidAudio("audio/x-m4a", "voice.m4a"));
    }

    @Test
    @DisplayName("验证合法的OGG音频")
    void testValidOggAudio() {
        assertTrue(validator.isValidAudio("audio/ogg", "music.ogg"));
    }

    @Test
    @DisplayName("验证合法的WebM音频")
    void testValidWebmAudio() {
        assertTrue(validator.isValidAudio("audio/webm", "audio.webm"));
    }

    @ParameterizedTest
    @CsvSource({
        "audio/mpeg, song.mp3",
        "audio/mp3, song.mp3",
        "audio/wav, recording.wav",
        "audio/wave, recording.wav",
        "audio/mp4, voice.m4a",
        "audio/ogg, music.ogg",
        "audio/webm, audio.webm"
    })
    @DisplayName("参数化测试：验证所有支持的音频类型")
    void testAllSupportedAudioTypes(String contentType, String filename) {
        assertTrue(validator.isValidAudio(contentType, filename));
    }

    @Test
    @DisplayName("验证不合法的音频MIME类型")
    void testInvalidAudioMimeType() {
        assertFalse(validator.isValidAudio("image/jpeg", "photo.jpg"));
        assertFalse(validator.isValidAudio("video/mp4", "video.mp4"));
        assertFalse(validator.isValidAudio("application/pdf", "document.pdf"));
    }

    @Test
    @DisplayName("验证MIME类型与扩展名不匹配的音频")
    void testMismatchedAudioTypeAndExtension() {
        // MIME类型正确，扩展名错误
        assertFalse(validator.isValidAudio("audio/mpeg", "song.txt"));
        assertFalse(validator.isValidAudio("audio/wav", "recording.pdf"));
    }

    @Test
    @DisplayName("验证音频类型的大小写敏感性")
    void testAudioTypeCaseInsensitive() {
        // MIME类型大小写不敏感
        assertTrue(validator.isValidAudio("AUDIO/MPEG", "song.mp3"));
        assertTrue(validator.isValidAudio("Audio/Wav", "recording.wav"));

        // 文件扩展名大小写不敏感
        assertTrue(validator.isValidAudio("audio/mpeg", "song.MP3"));
        assertTrue(validator.isValidAudio("audio/wav", "recording.WAV"));
    }

    @Test
    @DisplayName("验证音频类型为null的情况")
    void testAudioValidationWithNullInputs() {
        assertFalse(validator.isValidAudio(null, "song.mp3"));
        assertFalse(validator.isValidAudio("audio/mpeg", null));
        assertFalse(validator.isValidAudio(null, null));
    }

    // ==================== 文件大小验证测试 ====================

    @Test
    @DisplayName("验证合法的文件大小")
    void testValidFileSize() {
        assertTrue(validator.isValidFileSize(1024)); // 1KB
        assertTrue(validator.isValidFileSize(1024 * 1024)); // 1MB
        assertTrue(validator.isValidFileSize(10 * 1024 * 1024)); // 10MB
        assertTrue(validator.isValidFileSize(99 * 1024 * 1024)); // 99MB
    }

    @Test
    @DisplayName("验证边界值文件大小")
    void testBoundaryFileSize() {
        // 最小合法值
        assertTrue(validator.isValidFileSize(1));

        // 最大合法值（100MB）
        long maxSize = 100 * 1024 * 1024;
        assertTrue(validator.isValidFileSize(maxSize));

        // 超出最大值
        assertFalse(validator.isValidFileSize(maxSize + 1));
    }

    @Test
    @DisplayName("验证不合法的文件大小")
    void testInvalidFileSize() {
        // 零字节
        assertFalse(validator.isValidFileSize(0));

        // 负数
        assertFalse(validator.isValidFileSize(-1));
        assertFalse(validator.isValidFileSize(-1024));

        // 超出限制（>100MB）
        assertFalse(validator.isValidFileSize(101 * 1024 * 1024));
        assertFalse(validator.isValidFileSize(200 * 1024 * 1024));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 1024, 1024*1024, 10*1024*1024, 99*1024*1024, 100*1024*1024})
    @DisplayName("参数化测试：验证合法文件大小")
    void testValidFileSizesParameterized(long fileSize) {
        assertTrue(validator.isValidFileSize(fileSize));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -1024, 101*1024*1024, 200*1024*1024, Long.MAX_VALUE})
    @DisplayName("参数化测试：验证不合法文件大小")
    void testInvalidFileSizesParameterized(long fileSize) {
        assertFalse(validator.isValidFileSize(fileSize));
    }

    @Test
    @DisplayName("验证getMaxFileSize方法")
    void testGetMaxFileSize() {
        long maxSize = validator.getMaxFileSize();
        assertEquals(100 * 1024 * 1024, maxSize, "最大文件大小应为100MB");
    }

    // ==================== 边界条件和异常测试 ====================

    @Test
    @DisplayName("验证空字符串文件名")
    void testEmptyFilename() {
        assertFalse(validator.isValidImage("image/jpeg", ""));
        assertFalse(validator.isValidAudio("audio/mpeg", ""));
    }

    @Test
    @DisplayName("验证无扩展名的文件名")
    void testFilenameWithoutExtension() {
        assertFalse(validator.isValidImage("image/jpeg", "photo"));
        assertFalse(validator.isValidAudio("audio/mpeg", "song"));
    }

    @Test
    @DisplayName("验证以点结尾的文件名")
    void testFilenameEndingWithDot() {
        assertFalse(validator.isValidImage("image/jpeg", "photo."));
        assertFalse(validator.isValidAudio("audio/mpeg", "song."));
    }

    @Test
    @DisplayName("验证多个点的文件名")
    void testFilenameWithMultipleDots() {
        assertTrue(validator.isValidImage("image/jpeg", "my.photo.backup.jpg"));
        assertTrue(validator.isValidAudio("audio/mpeg", "my.song.backup.mp3"));
    }

    @Test
    @DisplayName("验证路径分隔符的文件名")
    void testFilenameWithPathSeparators() {
        assertTrue(validator.isValidImage("image/jpeg", "/path/to/photo.jpg"));
        assertTrue(validator.isValidAudio("audio/mpeg", "C:\\Users\\music\\song.mp3"));
    }

    @Test
    @DisplayName("验证空白字符的MIME类型")
    void testWhitespaceMimeType() {
        assertFalse(validator.isValidImage("  ", "photo.jpg"));
        assertFalse(validator.isValidAudio("  ", "song.mp3"));
    }

    @Test
    @DisplayName("验证非法字符的文件名")
    void testFilenameWithSpecialCharacters() {
        assertTrue(validator.isValidImage("image/jpeg", "photo@#$%^&().jpg"));
        assertTrue(validator.isValidAudio("audio/mpeg", "song(1) - copy.mp3"));
    }
}
