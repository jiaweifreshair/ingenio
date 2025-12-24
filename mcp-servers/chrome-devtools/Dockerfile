# Chrome DevTools MCP Server Dockerfile
# 基于Node.js 22官方镜像
FROM node:22-slim

# 安装Chrome和必要的依赖
RUN apt-get update && apt-get install -y \
    wget \
    gnupg \
    ca-certificates \
    fonts-liberation \
    libappindicator3-1 \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libc6 \
    libcairo2 \
    libcups2 \
    libdbus-1-3 \
    libexpat1 \
    libfontconfig1 \
    libgbm1 \
    libgcc1 \
    libglib2.0-0 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libpango-1.0-0 \
    libpangocairo-1.0-0 \
    libstdc++6 \
    libx11-6 \
    libx11-xcb1 \
    libxcb1 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxss1 \
    libxtst6 \
    lsb-release \
    xdg-utils \
    && wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google.list \
    && apt-get update \
    && apt-get install -y google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 安装chrome-devtools-mcp
RUN npm install -g chrome-devtools-mcp@latest

# 创建非root用户
RUN groupadd -r mcp && useradd -r -g mcp -G audio,video mcp \
    && mkdir -p /home/mcp/Downloads \
    && chown -R mcp:mcp /home/mcp \
    && chown -R mcp:mcp /app

# 切换到非root用户
USER mcp

# 暴露MCP服务端口（如果需要）
EXPOSE 3000

# 设置环境变量
ENV NODE_ENV=production
ENV CHROME_PATH=/usr/bin/google-chrome-stable

# 启动MCP服务器
# 注意：chrome-devtools-mcp通常通过stdio运行，不需要暴露端口
CMD ["npx", "chrome-devtools-mcp"]
