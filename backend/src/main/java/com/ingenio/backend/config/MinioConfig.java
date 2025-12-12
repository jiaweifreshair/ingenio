package com.ingenio.backend.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * MinIO配置类
 * 配置对象存储客户端
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /**
     * MinIO服务地址
     */
    private String endpoint;

    /**
     * MinIO访问密钥
     */
    private String accessKey;

    /**
     * MinIO私有密钥
     */
    private String secretKey;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 是否自动创建存储桶
     */
    private boolean autoCreateBucket = true;

    /**
     * 配置MinioClient Bean
     * 延迟初始化,避免启动时因MinIO服务未就绪导致失败
     */
    @Bean
    public MinioClient minioClient() {
        try {
            // 显式创建一个不使用代理的 OkHttpClient，防止系统代理导致 localhost 连接失败 (502 Bad Gateway)
            OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .proxy(Proxy.NO_PROXY) // 关键：强制不使用代理
                .build();
            
            String finalEndpoint = this.endpoint;
            // 如果是本地开发环境,强制覆盖endpoint,避免受环境变量影响
            if (activeProfile != null && activeProfile.contains("local")) {
                finalEndpoint = "http://127.0.0.1:9000";
                log.info("[MinioConfig] 检测到'local' profile, 强制覆盖MinIO endpoint为: {}", finalEndpoint);
            }

            // 仅创建客户端,不立即连接
            MinioClient minioClient = MinioClient.builder()
                .endpoint(finalEndpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();

            log.info("✅ MinIO客户端配置完成: {}", finalEndpoint);
            log.info("⚠️ 注意: 存储桶将在首次使用时创建");
            return minioClient;
        } catch (Exception e) {
            log.error("❌ MinIO客户端配置失败", e);
            throw new RuntimeException("MinIO客户端配置失败", e);
        }
    }
}
