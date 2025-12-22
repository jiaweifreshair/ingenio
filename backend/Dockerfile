# 简化Dockerfile - Ingenio后端服务（仅运行阶段）
# 使用openjdk官方镜像（无需Docker Hub认证）
FROM openjdk:17-jdk-slim

# 安装运行时依赖
RUN apt-get update && \
    apt-get install -y curl bash && \
    rm -rf /var/lib/apt/lists/*

# 创建非root用户
RUN groupadd -r ingenio && useradd -r -g ingenio ingenio

# 设置工作目录
WORKDIR /app

# 复制本地构建好的JAR文件
COPY target/ingenio-backend-0.1.0-SNAPSHOT.jar app.jar

# 修改所有权
RUN chown -R ingenio:ingenio /app

# 切换到应用用户
USER ingenio

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=docker", \
  "-jar", \
  "app.jar"]
