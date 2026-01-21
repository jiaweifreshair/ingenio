package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.request.OpenLovableGenerateRequest;
import com.ingenio.backend.dto.response.OpenLovableGenerateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Open-Lovable集成服务 - V2.0架构核心组件
 * 负责与Open-Lovable-CN服务集成，提供前端原型快速生成能力
 *
 * 功能：
 * 1. 生成前端原型（5-10秒快速生成）
 * 2. 支持网站爬取+生成
 * 3. 支持纯AI生成
 * 4. 支持定制化修改
 * 5. 沙箱管理（创建、查询、终止）
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenLovableService {

    @Value("${ingenio.openlovable.base-url:http://localhost:3001}")
    private String openLovableBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 线程池用于异步等待生成完成
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 生成前端原型（主方法）
     *
     * 流程：
     * 1. 验证请求参数
     * 2. 创建或复用沙箱
     * 3. 调用OpenLovable生成代码
     * 4. 等待生成完成（轮询或流式监听）
     * 5. 返回沙箱URL供用户预览
     *
     * @param request 生成请求
     * @return 生成响应（包含预览URL）
     * @throws BusinessException 当生成失败时抛出
     */
    public OpenLovableGenerateResponse generatePrototype(OpenLovableGenerateRequest request) {
        if (!request.isValid()) {
            log.error("OpenLovable生成请求无效: {}", request);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成请求参数无效");
        }

        log.info("开始OpenLovable前端原型生成: userRequirement={}, referenceUrls={}, crawling={}",
                request.getUserRequirement(),
                request.getReferenceUrls(),
                request.shouldCrawl());

        // 生成前快速检测依赖服务，避免用户长时间等待
        if (!isHealthy()) {
            log.error("OpenLovable服务健康检查失败: baseUrl={}", openLovableBaseUrl);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "OpenLovable服务未启动，请先运行OpenLovable-CN（默认端口: 3001）后再试");
        }

        long startTime = System.currentTimeMillis();
        Instant startInstant = Instant.now();

        try {
            // Step 1: 创建或获取沙箱
            String sandboxId = request.getSandboxId();
            String sandboxUrl;
            String provider;

            if (sandboxId == null || sandboxId.isEmpty()) {
                log.info("未提供sandboxId，开始创建新沙箱...");
                Map<String, Object> sandboxInfo = createSandbox();

                sandboxId = (String) sandboxInfo.get("sandboxId");
                sandboxUrl = (String) sandboxInfo.get("url");
                provider = (String) sandboxInfo.get("provider");

                log.info("沙箱创建成功: sandboxId={}, url={}, provider={}",
                        sandboxId, sandboxUrl, provider);
            } else {
                log.info("复用已有沙箱: sandboxId={}", sandboxId);
                Map<String, Object> statusInfo = getSandboxStatus(sandboxId);
                sandboxUrl = (String) statusInfo.getOrDefault("url", "");
                provider = (String) statusInfo.getOrDefault("provider", "unknown");
            }

            // Step 2: 构建生成提示词
            String prompt = request.buildPrompt();
            log.info("生成提示词: {}", prompt.substring(0, Math.min(prompt.length(), 200)));

            // Step 3: 调用OpenLovable生成API（异步触发）
            // V2.0优化：直接返回沙箱URL，代码生成在后台进行
            // open-lovable-cn自带30分钟超时策略，无需手动管理
            triggerGenerationAsync(prompt, request.getAiModel(), sandboxId);

            log.info("原型沙箱已创建，代码生成将在后台进行（open-lovable-cn管理超时）");

            // Step 4: 构建响应
            long durationMs = System.currentTimeMillis() - startTime;
            OpenLovableGenerateResponse response = OpenLovableGenerateResponse.builder()
                    .success(true)
                    .sandboxId(sandboxId)
                    .provider(provider)
                    .previewUrl(sandboxUrl)
                    .durationSeconds(durationMs / 1000)
                    .completedAt(Instant.now())
                    .crawled(request.shouldCrawl())
                    .crawledUrl(request.getPrimaryReferenceUrl())
                    .build();

            log.info("OpenLovable前端原型生成成功: sandboxId={}, previewUrl={}, duration={}s",
                    sandboxId, sandboxUrl, response.getDurationSeconds());

            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenLovable前端原型生成失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "前端原型生成失败: " + e.getMessage());
        }
    }

    /**
     * 预热沙箱（公开方法，供并行执行优化使用）
     *
     * V2.0性能优化：支持在意图识别的同时预创建沙箱，
     * 可节省约25-30秒的等待时间。
     *
     * @return 沙箱信息 (sandboxId, url, provider)
     */
    public Map<String, Object> prewarmSandbox() {
        log.info("开始预热沙箱...");
        long startTime = System.currentTimeMillis();

        Map<String, Object> sandboxInfo = createSandbox();

        long duration = System.currentTimeMillis() - startTime;
        log.info("沙箱预热完成: sandboxId={}, url={}, 耗时={}ms",
                sandboxInfo.get("sandboxId"),
                sandboxInfo.get("url"),
                duration);

        return sandboxInfo;
    }

    /**
     * 使用预创建的沙箱生成原型
     *
     * V2.0性能优化：如果已有预创建的沙箱，直接复用，跳过沙箱创建步骤
     *
     * @param request 生成请求
     * @param prewarmedSandbox 预创建的沙箱信息（可为null）
     * @return 生成响应
     */
    public OpenLovableGenerateResponse generatePrototypeWithPrewarmedSandbox(
            OpenLovableGenerateRequest request,
            Map<String, Object> prewarmedSandbox) {

        if (prewarmedSandbox != null && prewarmedSandbox.containsKey("sandboxId")) {
            // 使用预创建的沙箱
            String sandboxId = (String) prewarmedSandbox.get("sandboxId");
            String sandboxUrl = (String) prewarmedSandbox.get("url");
            String provider = (String) prewarmedSandbox.get("provider");

            log.info("使用预创建的沙箱: sandboxId={}", sandboxId);

            // 设置sandboxId到请求中
            OpenLovableGenerateRequest modifiedRequest = OpenLovableGenerateRequest.builder()
                    .userRequirement(request.getUserRequirement())
                    .referenceUrls(request.getReferenceUrls())
                    .needsCrawling(request.getNeedsCrawling())
                    .streaming(request.getStreaming())
                    .aiModel(request.getAiModel())
                    .timeoutSeconds(request.getTimeoutSeconds())
                    .blueprintFrontendSpec(request.getBlueprintFrontendSpec())
                    .blueprintModeEnabled(request.getBlueprintModeEnabled())
                    .sandboxId(sandboxId)
                    .build();

            return generatePrototypeInternal(modifiedRequest, sandboxId, sandboxUrl, provider);
        } else {
            // 没有预创建沙箱，走正常流程
            log.info("没有预创建沙箱，走正常流程");
            return generatePrototype(request);
        }
    }

    /**
     * 内部方法：使用已知沙箱信息生成原型
     */
    private OpenLovableGenerateResponse generatePrototypeInternal(
            OpenLovableGenerateRequest request,
            String sandboxId,
            String sandboxUrl,
            String provider) {

        log.info("使用已有沙箱生成原型: sandboxId={}", sandboxId);
        long startTime = System.currentTimeMillis();

        try {
            // 构建生成提示词
            String prompt = request.buildPrompt();
            log.info("生成提示词: {}", prompt.substring(0, Math.min(prompt.length(), 200)));

            // 调用OpenLovable生成API（异步触发）
            // V2.0优化：利用open-lovable-cn的完善超时策略
            triggerGenerationAsync(prompt, request.getAiModel(), sandboxId);

            // 构建响应
            long durationMs = System.currentTimeMillis() - startTime;
            return OpenLovableGenerateResponse.builder()
                    .success(true)
                    .sandboxId(sandboxId)
                    .provider(provider)
                    .previewUrl(sandboxUrl)
                    .durationSeconds(durationMs / 1000)
                    .completedAt(java.time.Instant.now())
                    .crawled(request.shouldCrawl())
                    .crawledUrl(request.getPrimaryReferenceUrl())
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("使用预创建沙箱生成原型失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "原型生成失败: " + e.getMessage());
        }
    }

    /**
     * 创建AI沙箱
     *
     * @return 沙箱信息 (sandboxId, url, provider)
     */
    private Map<String, Object> createSandbox() {
        try {
            String url = openLovableBaseUrl + "/api/create-ai-sandbox-v2";
            log.debug("调用创建沙箱API: {}", url);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                log.debug("沙箱创建响应: {}", body);

                // 从响应中提取沙箱信息
                if (body.containsKey("sandboxId") && body.containsKey("url")) {
                    return body;
                } else {
                    log.error("沙箱创建响应格式异常: {}", body);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "沙箱创建响应格式异常");
                }
            } else {
                log.error("沙箱创建失败: status={}", response.getStatusCode());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "沙箱创建失败");
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用沙箱创建API失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建沙箱失败: " + e.getMessage());
        }
    }

    /**
     * 获取沙箱状态
     *
     * @return 沙箱状态信息
     */
    private Map<String, Object> getSandboxStatus(String sandboxId) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(openLovableBaseUrl + "/api/sandbox-status");
            if (sandboxId != null && !sandboxId.isEmpty()) {
                builder.queryParam("sandboxId", sandboxId);
            }
            String url = builder.toUriString();
            log.debug("查询沙箱状态: {}", url);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("获取沙箱状态失败: status={}", response.getStatusCode());
                return new HashMap<>();
            }

        } catch (Exception e) {
            log.warn("查询沙箱状态异常", e);
            return new HashMap<>();
        }
    }

    /**
     * 触发代码生成（异步，fire-and-forget）
     * V2.0优化：使用 /api/generate-ai-code-stream 触发生成，open-lovable-cn自动管理超时
     *
     * @param prompt 生成提示词
     * @param aiModel AI模型名称
     * @param sandboxId 沙箱ID
     */
    private void triggerGenerationAsync(String prompt, String aiModel, String sandboxId) {
        executorService.submit(() -> {
            try {
                String url = openLovableBaseUrl + "/api/generate-ai-code-stream";
                log.info("[异步] 触发代码生成: sandboxId={}, model={}", sandboxId, aiModel);

                // 构建请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("prompt", prompt);
                requestBody.put("model", aiModel);
                requestBody.put("isEdit", false);

                // 构建context对象
                Map<String, Object> context = new HashMap<>();
                context.put("sandboxId", sandboxId);
                requestBody.put("context", context);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

                // 发送POST请求（触发生成）
                @SuppressWarnings("unchecked")
                ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

                log.info("[异步] 代码生成完成: sandboxId={}, status={}", sandboxId, response.getStatusCode());

            } catch (Exception e) {
                log.error("[异步] 代码生成失败: sandboxId={}", sandboxId, e);
            }
        });
    }

    /**
     * 触发代码生成（非流式）
     * @deprecated 使用 triggerGenerationAsync 替代
     *
     * @param prompt 生成提示词
     * @param aiModel AI模型名称
     */
    @Deprecated
    private void triggerGeneration(String prompt, String aiModel, String sandboxId) {
        try {
            String url = openLovableBaseUrl + "/api/generate-ai-code";
            log.debug("触发代码生成: url={}, model={}", url, aiModel);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userMessage", prompt);
            requestBody.put("model", aiModel);
            if (sandboxId != null && !sandboxId.isEmpty()) {
                Map<String, Object> context = new HashMap<>();
                context.put("sandboxId", sandboxId);
                requestBody.put("context", context);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // 发送POST请求（异步，不等待完成）
            // 注意：Open-Lovable的生成API可能是异步的，立即返回
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            log.info("代码生成触发成功: status={}, body={}", response.getStatusCode(), response.getBody());

        } catch (Exception e) {
            log.error("触发代码生成失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "触发代码生成失败: " + e.getMessage());
        }
    }

    /**
     * 等待代码生成完成（优化版本：轮询机制）
     *
     * 策略：每2秒查询沙箱状态，生成完成立即返回
     * 相比固定等待30秒，可节省15-25秒延迟
     *
     * @param timeoutSeconds 超时时间（秒）
     */
    private void waitForGenerationComplete(int timeoutSeconds, String sandboxId) {
        log.info("开始轮询等待代码生成完成，超时时间: {}秒", timeoutSeconds);

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        int pollIntervalMs = 2000; // 每2秒轮询一次
        int pollCount = 0;

        try {
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                pollCount++;

                // 查询沙箱状态
                Map<String, Object> status = getSandboxStatus(sandboxId);
                String sandboxStatus = (String) status.getOrDefault("status", "unknown");
                Boolean isGenerating = (Boolean) status.getOrDefault("isGenerating", false);
                Boolean hasFiles = (Boolean) status.getOrDefault("hasFiles", false);

                log.debug("轮询#{}: status={}, isGenerating={}, hasFiles={}",
                        pollCount, sandboxStatus, isGenerating, hasFiles);

                // 判断生成是否完成：
                // 1. 沙箱状态为ready/running
                // 2. 不再生成中（isGenerating=false）
                // 3. 有文件生成（hasFiles=true）
                if (!Boolean.TRUE.equals(isGenerating) && Boolean.TRUE.equals(hasFiles)) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("代码生成完成: 耗时{}ms, 轮询{}次", elapsed, pollCount);
                    return;
                }

                // 备选判断：如果沙箱状态返回空或错误，使用最小等待时间
                if (status.isEmpty() || "error".equals(sandboxStatus)) {
                    log.warn("沙箱状态查询失败或错误，使用最小等待时间");
                    // 最小等待5秒后检查
                    if (System.currentTimeMillis() - startTime >= 5000) {
                        log.info("最小等待时间已到，假定生成完成");
                        return;
                    }
                }

                // 等待下一次轮询
                Thread.sleep(pollIntervalMs);
            }

            // 超时
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("代码生成等待超时: 耗时{}ms, 轮询{}次", elapsed, pollCount);

        } catch (InterruptedException e) {
            log.warn("等待被中断", e);
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成被中断");
        }
    }

    /**
     * 终止沙箱
     *
     * @return true 如果终止成功
     */
    public boolean killSandbox() {
        try {
            String url = openLovableBaseUrl + "/api/kill-sandbox";
            log.info("终止沙箱: {}", url);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("沙箱终止结果: success={}", success);

            return success;

        } catch (Exception e) {
            log.error("终止沙箱失败", e);
            return false;
        }
    }

    /**
     * 健康检查
     *
     * @return true 如果服务可用
     */
    public boolean isHealthy() {
        try {
            String url = openLovableBaseUrl + "/api/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            boolean healthy = response.getStatusCode().is2xxSuccessful();
            log.debug("Open-Lovable健康检查: healthy={}", healthy);

            return healthy;

        } catch (Exception e) {
            log.warn("Open-Lovable健康检查失败", e);
            return false;
        }
    }

    /**
     * 资源清理
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 生成原型预览（SSE流式响应）
     * V2.0优化：直接代理 open-lovable-cn 的 SSE 流，支持实时进度显示（打字机效果）
     *
     * @param prompt 生成提示词
     * @param sandboxId 沙箱ID（可选，为null时自动创建）
     * @param referenceUrls 参考URL列表
     * @param customization 定制化需求
     * @param needsCrawling 是否需要爬取
     * @param outputStream 输出流
     * @throws Exception 生成失败时抛出
     */
    public void generatePrototypeStream(
            String prompt,
            String sandboxId,
            java.util.List<String> referenceUrls,
            String customization,
            boolean needsCrawling,
            java.io.OutputStream outputStream) throws Exception {

        log.info("[SSE Stream] 开始流式生成: sandboxId={}, needsCrawling={}", sandboxId, needsCrawling);

        try {
            // Step 1: 创建或获取沙箱
            String actualSandboxId = sandboxId;
            String sandboxUrl = null;
            String provider = null;

            if (actualSandboxId == null || actualSandboxId.isEmpty()) {
                log.info("[SSE Stream] 创建新沙箱...");

                // 发送进度事件：创建沙箱中
                String progressEvent = "data: {\"type\":\"progress\",\"stage\":\"sandbox\",\"message\":\"正在创建沙箱...\"}\n\n";
                outputStream.write(progressEvent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outputStream.flush();

                Map<String, Object> sandboxInfo = createSandbox();
                actualSandboxId = (String) sandboxInfo.get("sandboxId");
                sandboxUrl = (String) sandboxInfo.get("url");
                provider = (String) sandboxInfo.get("provider");

                log.info("[SSE Stream] 沙箱创建成功: sandboxId={}, url={}", actualSandboxId, sandboxUrl);

                // 发送沙箱信息事件（注意：前端期望字段名为sandboxUrl而非url）
                String sandboxEvent = String.format(
                    "data: {\"type\":\"sandbox\",\"sandboxId\":\"%s\",\"sandboxUrl\":\"%s\",\"provider\":\"%s\"}\n\n",
                    actualSandboxId, sandboxUrl, provider
                );
                outputStream.write(sandboxEvent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outputStream.flush();
            }

            // Step 2: 流式代理 open-lovable-cn 的代码生成API
            log.info("[SSE Stream] 开始流式代码生成...");

            // 发送进度事件：代码生成中
            String genProgressEvent = "data: {\"type\":\"progress\",\"stage\":\"generation\",\"message\":\"正在生成代码...\"}\n\n";
            outputStream.write(genProgressEvent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            outputStream.flush();

            String url = openLovableBaseUrl + "/api/generate-ai-code-stream";

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", prompt);
            requestBody.put("model", "gemini-3-pro-preview");  // 默认使用 Gemini 3 Pro
            requestBody.put("isEdit", false);

            Map<String, Object> context = new HashMap<>();
            context.put("sandboxId", actualSandboxId);
            requestBody.put("context", context);

            // 使用HttpURLConnection进行SSE流式转发
            java.net.URL targetUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(30000);  // 连接超时30秒
            connection.setReadTimeout(0);          // 读取超时设为0（无限），让open-lovable-cn管理超时

            // 发送请求体
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            connection.getOutputStream().write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            connection.getOutputStream().flush();

            // 读取并转发SSE流，同时聚合stream事件为conversation事件
            StringBuilder streamBuffer = new StringBuilder();
            try (java.io.InputStream inputStream = connection.getInputStream();
                 java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    // 转发原始SSE消息
                    outputStream.write((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    outputStream.flush();

                    // 解析stream事件并累积文本
                    if (line.startsWith("data: ")) {
                        try {
                            String jsonData = line.substring(6); // 移除 "data: " 前缀
                            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(jsonData);

                            if (node.has("type") && "stream".equals(node.get("type").asText())) {
                                if (node.has("text")) {
                                    String text = node.get("text").asText();
                                    streamBuffer.append(text);

                                    // 检查是否包含完整的文件标签
                                    String accumulated = streamBuffer.toString();
                                    if (accumulated.contains("<file") && accumulated.contains("</file>")) {
                                        log.info("[SSE Stream] 检测到文件标签，发送conversation事件");

                                        // 发送conversation事件
                                        String conversationEvent = String.format(
                                            "data: {\"type\":\"conversation\",\"text\":%s}\n\n",
                                            objectMapper.writeValueAsString(accumulated)
                                        );
                                        outputStream.write(conversationEvent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                        outputStream.flush();

                                        // 清空buffer继续收集后续文件
                                        // 但保留可能的不完整标签
                                        int lastFileEnd = accumulated.lastIndexOf("</file>");
                                        if (lastFileEnd != -1) {
                                            streamBuffer.setLength(0);
                                            streamBuffer.append(accumulated.substring(lastFileEnd + 7));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 忽略JSON解析错误，继续转发
                            log.debug("[SSE Stream] JSON解析失败: {}", e.getMessage());
                        }
                    }

                    log.debug("[SSE Stream] 转发: {}", line.substring(0, Math.min(line.length(), 100)));
                }

                // 流结束时，如果还有未发送的内容（包含文件标签），发送最后一个conversation事件
                if (streamBuffer.length() > 0) {
                    String accumulated = streamBuffer.toString();
                    if (accumulated.contains("<file") && accumulated.contains("</file>")) {
                        log.info("[SSE Stream] 流结束，发送最后的conversation事件");
                        String conversationEvent = String.format(
                            "data: {\"type\":\"conversation\",\"text\":%s}\n\n",
                            objectMapper.writeValueAsString(accumulated)
                        );
                        outputStream.write(conversationEvent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        outputStream.flush();
                    }
                }
            }

            log.info("[SSE Stream] 流式生成完成: sandboxId={}", actualSandboxId);

        } catch (Exception e) {
            log.error("[SSE Stream] 流式生成失败", e);

            // 发送错误事件
            String errorEvent = String.format(
                "data: {\"type\":\"error\",\"error\":\"%s\"}\n\n",
                e.getMessage().replace("\"", "\\\"")
            );
            outputStream.write(errorEvent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            outputStream.flush();

            throw e;
        }
    }
}
