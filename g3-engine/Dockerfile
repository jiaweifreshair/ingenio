FROM python:3.10-slim

WORKDIR /app

COPY requirements.txt .
# 使用阿里云镜像加速（可选，视网络情况而定）
# RUN pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["python", "main.py"]
