package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 语音转文字服务
 * 使用阿里云DashScope语音识别API（Paraformer模型）
 * 
 * API文档: https://help.aliyun.com/zh/dashscope/developer-reference/paraformer-api
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTranscriptionService {

    @Value("${DASHSCOPE_API_KEY:}")
    private String apiKey;

    private final MinioService minioService;
    private final ObjectMapper objectMapper;

    private static final String DASHSCOPE_ASR_API = "https://dashscope.aliyuncs.com/api/v1/services/audio/asr";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 语音转文字（从MinIO URL）
     * 
     * @param audioUrl MinIO中的音频文件URL
     * @return 转录文本
     */
    public String transcribe(String audioUrl) {
        log.info("开始语音转文字: audioUrl={}", audioUrl);

        try {
            // 1. 从MinIO下载音频文件到临时文件
            File tempFile = downloadAudioToTemp(audioUrl);

            // 2. 调用DashScope语音识别API
            String transcript = callDashScopeASR(tempFile);

            // 3. 删除临时文件
            Files.deleteIfExists(tempFile.toPath());

            log.info("语音转文字完成: audioUrl={}, transcriptLength={}", 
                    audioUrl, transcript.length());

            return transcript;

        } catch (Exception e) {
            log.error("语音转文字失败: audioUrl={}", audioUrl, e);
            throw new RuntimeException("语音转文字失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从MinIO下载音频文件到临时文件
     */
    private File downloadAudioToTemp(String audioUrl) throws Exception {
        log.debug("从MinIO下载音频: url={}", audioUrl);

        // 生成临时文件名
        String tempFileName = "/tmp/audio_" + UUID.randomUUID() + ".mp3";
        File tempFile = new File(tempFileName);

        // 从MinIO下载
        try (InputStream inputStream = new URL(audioUrl).openStream();
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        log.debug("音频下载完成: tempFile={}, size={} bytes", 
                tempFile.getAbsolutePath(), tempFile.length());

        return tempFile;
    }

    /**
     * 调用DashScope语音识别API
     * 
     * API文档: https://help.aliyun.com/zh/dashscope/developer-reference/paraformer-api
     */
    private String callDashScopeASR(File audioFile) throws Exception {
        log.debug("调用DashScope ASR API: file={}", audioFile.getName());

        // 1. 构造请求体（multipart/form-data）
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "paraformer-v1")  // 使用Paraformer模型
                .addFormDataPart("file", audioFile.getName(),
                        RequestBody.create(audioFile, MediaType.parse("audio/mpeg")))
                .addFormDataPart("format", "pcm")
                .addFormDataPart("sample_rate", "16000")
                .build();

        // 2. 构造请求
        Request request = new Request.Builder()
                .url(DASHSCOPE_ASR_API)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        // 3. 发送请求
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new RuntimeException("DashScope API调用失败: " + response.code() + ", " + errorBody);
            }

            // 4. 解析响应
            String responseBody = response.body().string();
            log.debug("DashScope ASR响应: {}", responseBody);

            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            // 5. 提取转录文本
            if (jsonResponse.has("output") && jsonResponse.get("output").has("text")) {
                String transcript = jsonResponse.get("output").get("text").asText();
                return transcript;
            } else {
                throw new RuntimeException("DashScope API响应格式异常: " + responseBody);
            }
        }
    }

    /**
     * 检查API密钥是否配置
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("sk-placeholder");
    }
}
