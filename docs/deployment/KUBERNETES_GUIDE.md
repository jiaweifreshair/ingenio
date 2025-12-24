# Ingenio Backend Kubernetes 部署指南

本文档详细介绍如何在Kubernetes集群上部署Ingenio后端服务，实现生产级的高可用架构。

## 目录

- [1. K8s部署架构](#1-k8s部署架构)
- [2. 前置准备](#2-前置准备)
- [3. 命名空间和资源配额](#3-命名空间和资源配额)
- [4. ConfigMap和Secret管理](#4-configmap和secret管理)
- [5. StatefulSet部署](#5-statefulset部署)
- [6. Deployment配置](#6-deployment配置)
- [7. Service和Ingress](#7-service和ingress)
- [8. 水平扩展(HPA)](#8-水平扩展hpa)
- [9. 滚动更新和回滚](#9-滚动更新和回滚)
- [10. 监控和告警](#10-监控和告警)
- [11. 日志聚合](#11-日志聚合)
- [12. 备份和恢复](#12-备份和恢复)
- [13. 故障排查](#13-故障排查)

---

## 1. K8s部署架构

### 1.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    Ingress Controller                            │
│              (Nginx/Traefik/AWS ALB)                             │
│                        HTTPS                                     │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────┴───────────────────────────────────────┐
│                       Service Layer                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐│
│  │Backend Service  │  │Postgres Service │  │ Redis Service   ││
│  │  ClusterIP      │  │   ClusterIP     │  │  ClusterIP      ││
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘│
└───────────┼────────────────────┼────────────────────┼──────────┘
            │                    │                    │
┌───────────┼────────────────────┼────────────────────┼──────────┐
│           │                    │                    │           │
│  ┌────────▼────────┐  ┌────────▼─────────┐  ┌──────▼───────┐  │
│  │Backend Pod 1    │  │Postgres Master   │  │Redis Master  │  │
│  │Backend Pod 2    │  │  StatefulSet-0   │  │StatefulSet-0 │  │
│  │Backend Pod 3    │  ├──────────────────┤  ├──────────────┤  │
│  │  Deployment     │  │Postgres Replica  │  │Redis Replica │  │
│  │                 │  │  StatefulSet-1   │  │StatefulSet-1 │  │
│  └─────────────────┘  └──────────────────┘  └──────────────┘  │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Persistent Volume Claims                   │   │
│  │  PVC: postgres-data, redis-data, minio-data             │   │
│  └─────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 1.2 资源清单

| 资源类型 | 名称 | 副本数 | 用途 |
|---------|------|--------|------|
| **Deployment** | ingenio-backend | 3 | 后端服务 |
| **StatefulSet** | postgres | 2 (主从) | PostgreSQL数据库 |
| **StatefulSet** | redis | 3 (哨兵) | Redis缓存 |
| **StatefulSet** | minio | 4 | MinIO对象存储 |
| **Service** | backend-svc | - | Backend服务发现 |
| **Service** | postgres-svc | - | PostgreSQL服务发现 |
| **Service** | redis-svc | - | Redis服务发现 |
| **Ingress** | ingenio-ingress | - | HTTPS入口 |
| **ConfigMap** | backend-config | - | 应用配置 |
| **Secret** | backend-secret | - | 敏感信息 |
| **HPA** | backend-hpa | - | 自动扩展 |
| **PVC** | postgres-pvc | - | 数据库持久化 |

---

## 2. 前置准备

### 2.1 Kubernetes集群要求

#### 最小规格

| 组件 | 要求 |
|-----|------|
| **Kubernetes版本** | 1.25+ |
| **节点数量** | 3+ (生产环境) |
| **单节点配置** | 4核8GB+ |
| **存储类** | 支持动态PV分配 |

#### 推荐规格

```yaml
生产集群规格:
  Master节点: 3个, 每个4核16GB
  Worker节点: 5个, 每个8核32GB
  存储后端: Ceph/NFS/Cloud Storage
  网络插件: Calico/Flannel/Cilium
```

### 2.2 必需工具安装

```bash
# kubectl (K8s命令行工具)
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Helm (K8s包管理器)
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# kustomize (配置管理)
curl -s "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" | bash

# kubectx + kubens (上下文切换)
sudo git clone https://github.com/ahmetb/kubectx /opt/kubectx
sudo ln -s /opt/kubectx/kubectx /usr/local/bin/kubectx
sudo ln -s /opt/kubectx/kubens /usr/local/bin/kubens
```

### 2.3 验证集群

```bash
# 检查集群状态
kubectl cluster-info

# 查看节点
kubectl get nodes

# 查看存储类
kubectl get storageclass

# 检查命名空间
kubectl get namespaces
```

---

## 3. 命名空间和资源配额

### 3.1 创建命名空间

创建 `namespace.yaml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ingenio
  labels:
    name: ingenio
    env: production

---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: ingenio-quota
  namespace: ingenio
spec:
  hard:
    requests.cpu: "20"
    requests.memory: 40Gi
    limits.cpu: "40"
    limits.memory: 80Gi
    persistentvolumeclaims: "10"
    services.loadbalancers: "2"

---
apiVersion: v1
kind: LimitRange
metadata:
  name: ingenio-limits
  namespace: ingenio
spec:
  limits:
  - max:
      cpu: "4"
      memory: 8Gi
    min:
      cpu: "100m"
      memory: 128Mi
    default:
      cpu: "500m"
      memory: 512Mi
    defaultRequest:
      cpu: "200m"
      memory: 256Mi
    type: Container
```

应用配置:

```bash
kubectl apply -f namespace.yaml

# 切换到ingenio命名空间
kubens ingenio
```

---

## 4. ConfigMap和Secret管理

### 4.1 ConfigMap配置

创建 `configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: backend-config
  namespace: ingenio
data:
  # Spring配置
  SPRING_PROFILES_ACTIVE: "prod"

  # 数据库配置
  DB_HOST: "postgres-svc.ingenio.svc.cluster.local"
  DB_PORT: "5432"
  DB_NAME: "ingenio_prod"

  # Redis配置
  REDIS_HOST: "redis-svc.ingenio.svc.cluster.local"
  REDIS_PORT: "6379"

  # MinIO配置
  MINIO_ENDPOINT: "http://minio-svc.ingenio.svc.cluster.local:9000"
  MINIO_BUCKET: "ingenio"

  # JVM配置
  JAVA_OPTS: |-
    -Xms2g
    -Xmx4g
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:ParallelGCThreads=4
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/app/logs/heap_dump.hprof

  # 应用配置
  application.yml: |-
    server:
      port: 8080
      compression:
        enabled: true

    spring:
      datasource:
        hikari:
          maximum-pool-size: 50
          minimum-idle: 10
          connection-timeout: 30000

      redis:
        lettuce:
          pool:
            max-active: 20
            max-idle: 10

    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      metrics:
        export:
          prometheus:
            enabled: true
```

### 4.2 Secret管理

#### 创建Secret (命令行方式)

```bash
# 创建数据库密码
kubectl create secret generic db-credentials \
  --namespace=ingenio \
  --from-literal=username=ingenio_user \
  --from-literal=password='your-strong-password'

# 创建Redis密码
kubectl create secret generic redis-credentials \
  --namespace=ingenio \
  --from-literal=password='your-redis-password'

# 创建MinIO密钥
kubectl create secret generic minio-credentials \
  --namespace=ingenio \
  --from-literal=access-key='your-minio-access-key' \
  --from-literal=secret-key='your-minio-secret-key'

# 创建JWT密钥
kubectl create secret generic jwt-secret \
  --namespace=ingenio \
  --from-literal=secret='your-jwt-secret-key'

# 创建DeepSeek API密钥
kubectl create secret generic deepseek-api \
  --namespace=ingenio \
  --from-literal=api-key='sk-your-deepseek-key'
```

#### 创建Secret (YAML方式)

创建 `secrets.yaml`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: backend-secret
  namespace: ingenio
type: Opaque
stringData:
  # 数据库凭证
  DB_USER: ingenio_user
  DB_PASSWORD: your-strong-password

  # Redis密码
  REDIS_PASSWORD: your-redis-password

  # MinIO凭证
  MINIO_ACCESS_KEY: your-minio-access-key
  MINIO_SECRET_KEY: your-minio-secret-key

  # JWT密钥
  JWT_SECRET: your-jwt-secret-key

  # DeepSeek API密钥
  DEEPSEEK_API_KEY: sk-your-deepseek-key
```

**⚠️ 注意**: 不要将 `secrets.yaml` 提交到Git仓库！

#### 使用Sealed Secrets（推荐生产环境）

```bash
# 安装Sealed Secrets Controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/controller.yaml

# 安装kubeseal CLI
KUBESEAL_VERSION='0.24.0'
wget "https://github.com/bitnami-labs/sealed-secrets/releases/download/v${KUBESEAL_VERSION}/kubeseal-${KUBESEAL_VERSION}-linux-amd64.tar.gz"
tar -xvzf kubeseal-${KUBESEAL_VERSION}-linux-amd64.tar.gz kubeseal
sudo install -m 755 kubeseal /usr/local/bin/kubeseal

# 加密Secret
kubectl create secret generic backend-secret \
  --namespace=ingenio \
  --from-literal=DB_PASSWORD='your-password' \
  --dry-run=client -o yaml | \
  kubeseal -o yaml > sealed-secret.yaml

# 应用加密后的Secret
kubectl apply -f sealed-secret.yaml
```

---

## 5. StatefulSet部署

### 5.1 PostgreSQL StatefulSet

创建 `postgres-statefulset.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres-svc
  namespace: ingenio
  labels:
    app: postgres
spec:
  clusterIP: None  # Headless Service
  ports:
    - port: 5432
      name: postgres
  selector:
    app: postgres

---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: ingenio
spec:
  serviceName: postgres-svc
  replicas: 2
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
          name: postgres
        env:
        - name: POSTGRES_DB
          value: ingenio_prod
        - name: POSTGRES_USER
          valueFrom:
            secretKeyRef:
              name: backend-secret
              key: DB_USER
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: backend-secret
              key: DB_PASSWORD
        - name: PGDATA
          value: /var/lib/postgresql/data/pgdata
        volumeMounts:
        - name: postgres-data
          mountPath: /var/lib/postgresql/data
        - name: postgres-config
          mountPath: /docker-entrypoint-initdb.d
          readOnly: true
        resources:
          requests:
            cpu: "500m"
            memory: "2Gi"
          limits:
            cpu: "2"
            memory: "4Gi"
        livenessProbe:
          exec:
            command:
            - pg_isready
            - -U
            - ingenio_user
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          exec:
            command:
            - pg_isready
            - -U
            - ingenio_user
          initialDelaySeconds: 5
          periodSeconds: 5
          timeoutSeconds: 3
      volumes:
      - name: postgres-config
        configMap:
          name: postgres-init-scripts
  volumeClaimTemplates:
  - metadata:
      name: postgres-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: fast-ssd  # 根据集群修改
      resources:
        requests:
          storage: 100Gi
```

### 5.2 Redis StatefulSet (哨兵模式)

创建 `redis-statefulset.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: redis-config
  namespace: ingenio
data:
  redis.conf: |-
    bind 0.0.0.0
    protected-mode yes
    port 6379
    requirepass ${REDIS_PASSWORD}
    appendonly yes
    appendfsync everysec
    maxmemory 2gb
    maxmemory-policy allkeys-lru

---
apiVersion: v1
kind: Service
metadata:
  name: redis-svc
  namespace: ingenio
spec:
  clusterIP: None
  ports:
  - port: 6379
    name: redis
  selector:
    app: redis

---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
  namespace: ingenio
spec:
  serviceName: redis-svc
  replicas: 3
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        command:
        - redis-server
        - /etc/redis/redis.conf
        - --requirepass
        - $(REDIS_PASSWORD)
        ports:
        - containerPort: 6379
          name: redis
        env:
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: backend-secret
              key: REDIS_PASSWORD
        volumeMounts:
        - name: redis-data
          mountPath: /data
        - name: redis-config
          mountPath: /etc/redis
        resources:
          requests:
            cpu: "200m"
            memory: "512Mi"
          limits:
            cpu: "1"
            memory: "2Gi"
        livenessProbe:
          exec:
            command:
            - redis-cli
            - --raw
            - incr
            - ping
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          exec:
            command:
            - redis-cli
            - ping
          initialDelaySeconds: 5
          periodSeconds: 5
      volumes:
      - name: redis-config
        configMap:
          name: redis-config
  volumeClaimTemplates:
  - metadata:
      name: redis-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: fast-ssd
      resources:
        requests:
          storage: 20Gi
```

### 5.3 MinIO StatefulSet

创建 `minio-statefulset.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: minio-svc
  namespace: ingenio
spec:
  clusterIP: None
  ports:
  - port: 9000
    name: api
  - port: 9001
    name: console
  selector:
    app: minio

---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: minio
  namespace: ingenio
spec:
  serviceName: minio-svc
  replicas: 4
  selector:
    matchLabels:
      app: minio
  template:
    metadata:
      labels:
        app: minio
    spec:
      containers:
      - name: minio
        image: minio/minio:latest
        command:
        - minio
        - server
        - http://minio-{0...3}.minio-svc.ingenio.svc.cluster.local/data
        - --console-address
        - ":9001"
        env:
        - name: MINIO_ROOT_USER
          valueFrom:
            secretKeyRef:
              name: backend-secret
              key: MINIO_ACCESS_KEY
        - name: MINIO_ROOT_PASSWORD
          valueFrom:
            secretKeyRef:
              name: backend-secret
              key: MINIO_SECRET_KEY
        ports:
        - containerPort: 9000
          name: api
        - containerPort: 9001
          name: console
        volumeMounts:
        - name: minio-data
          mountPath: /data
        resources:
          requests:
            cpu: "500m"
            memory: "1Gi"
          limits:
            cpu: "2"
            memory: "4Gi"
        livenessProbe:
          httpGet:
            path: /minio/health/live
            port: 9000
          initialDelaySeconds: 30
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /minio/health/ready
            port: 9000
          initialDelaySeconds: 10
          periodSeconds: 10
  volumeClaimTemplates:
  - metadata:
      name: minio-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: fast-ssd
      resources:
        requests:
          storage: 100Gi
```

---

## 6. Deployment配置

### 6.1 Backend Deployment

创建 `backend-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ingenio-backend
  namespace: ingenio
  labels:
    app: ingenio-backend
    version: v1.0.0
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: ingenio-backend
  template:
    metadata:
      labels:
        app: ingenio-backend
        version: v1.0.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      # 亲和性配置 - 分散到不同节点
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - ingenio-backend
              topologyKey: kubernetes.io/hostname

      # Init容器 - 等待数据库就绪
      initContainers:
      - name: wait-for-postgres
        image: busybox:1.35
        command:
        - sh
        - -c
        - |
          until nc -zv postgres-svc 5432; do
            echo "Waiting for PostgreSQL..."
            sleep 2
          done
      - name: wait-for-redis
        image: busybox:1.35
        command:
        - sh
        - -c
        - |
          until nc -zv redis-svc 6379; do
            echo "Waiting for Redis..."
            sleep 2
          done

      # 应用容器
      containers:
      - name: backend
        image: ingenio-backend:v1.0.0  # 修改为实际镜像
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP

        # 环境变量 - 从ConfigMap和Secret注入
        envFrom:
        - configMapRef:
            name: backend-config
        - secretRef:
            name: backend-secret

        # 资源限制
        resources:
          requests:
            cpu: "500m"
            memory: "1Gi"
          limits:
            cpu: "2"
            memory: "4Gi"

        # 挂载卷
        volumeMounts:
        - name: logs
          mountPath: /app/logs
        - name: config
          mountPath: /app/config
          readOnly: true

        # 健康检查
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3

        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2

        # 启动探针
        startupProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 0
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 30  # 允许5分钟启动时间

        # 生命周期钩子
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 15"]  # 优雅关闭

      # 数据卷
      volumes:
      - name: logs
        emptyDir: {}
      - name: config
        configMap:
          name: backend-config

      # 服务账户
      serviceAccountName: ingenio-backend

      # 终止宽限期
      terminationGracePeriodSeconds: 30

      # 镜像拉取密钥（如果使用私有镜像仓库）
      # imagePullSecrets:
      # - name: regcred

---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: ingenio-backend
  namespace: ingenio
```

### 6.2 应用Deployment

```bash
# 应用所有配置
kubectl apply -f namespace.yaml
kubectl apply -f configmap.yaml
kubectl apply -f secrets.yaml
kubectl apply -f postgres-statefulset.yaml
kubectl apply -f redis-statefulset.yaml
kubectl apply -f minio-statefulset.yaml
kubectl apply -f backend-deployment.yaml

# 查看部署状态
kubectl get all -n ingenio

# 查看Pod日志
kubectl logs -f deployment/ingenio-backend -n ingenio
```

---

## 7. Service和Ingress

### 7.1 Backend Service

创建 `backend-service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: backend-svc
  namespace: ingenio
  labels:
    app: ingenio-backend
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: ingenio-backend
  sessionAffinity: ClientIP
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 10800  # 3小时
```

### 7.2 Ingress配置

创建 `ingress.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingenio-ingress
  namespace: ingenio
  annotations:
    # Nginx Ingress配置
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"

    # 速率限制
    nginx.ingress.kubernetes.io/limit-rps: "10"
    nginx.ingress.kubernetes.io/limit-connections: "20"

    # 超时配置
    nginx.ingress.kubernetes.io/proxy-connect-timeout: "60"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "60"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"

    # 负载均衡算法
    nginx.ingress.kubernetes.io/load-balance: "least_conn"

    # CORS配置
    nginx.ingress.kubernetes.io/enable-cors: "true"
    nginx.ingress.kubernetes.io/cors-allow-origin: "https://ingenio.com"
    nginx.ingress.kubernetes.io/cors-allow-methods: "GET, POST, PUT, DELETE, OPTIONS"

    # SSL证书管理器
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - api.ingenio.com
    secretName: ingenio-tls-secret
  rules:
  - host: api.ingenio.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: backend-svc
            port:
              number: 80
```

### 7.3 安装Cert-Manager（自动SSL证书）

```bash
# 安装Cert-Manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# 创建Let's Encrypt ClusterIssuer
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@ingenio.com
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
    - http01:
        ingress:
          class: nginx
EOF
```

---

## 8. 水平扩展(HPA)

### 8.1 HPA配置

创建 `hpa.yaml`:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-hpa
  namespace: ingenio
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ingenio-backend
  minReplicas: 3
  maxReplicas: 10
  metrics:
  # CPU利用率
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  # 内存利用率
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  # 自定义指标 - HTTP请求率
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "1000"
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300  # 5分钟稳定窗口
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
      - type: Pods
        value: 2
        periodSeconds: 60
      selectPolicy: Min
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 4
        periodSeconds: 30
      selectPolicy: Max
```

### 8.2 安装Metrics Server

```bash
# 安装Metrics Server
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# 验证Metrics Server
kubectl top nodes
kubectl top pods -n ingenio
```

### 8.3 自定义指标（使用Prometheus Adapter）

```bash
# 安装Prometheus Adapter
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install prometheus-adapter prometheus-community/prometheus-adapter \
  --namespace monitoring \
  --set prometheus.url=http://prometheus-server.monitoring.svc \
  --set prometheus.port=80

# 查看自定义指标
kubectl get --raw /apis/custom.metrics.k8s.io/v1beta1 | jq .
```

---

## 9. 滚动更新和回滚

### 9.1 更新策略

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1        # 最多多启动1个Pod
      maxUnavailable: 0  # 保证可用性
```

### 9.2 执行滚动更新

```bash
# 更新镜像
kubectl set image deployment/ingenio-backend \
  backend=ingenio-backend:v1.0.1 \
  -n ingenio

# 或者应用新的YAML
kubectl apply -f backend-deployment.yaml

# 查看更新状态
kubectl rollout status deployment/ingenio-backend -n ingenio

# 查看更新历史
kubectl rollout history deployment/ingenio-backend -n ingenio
```

### 9.3 回滚

```bash
# 回滚到上一个版本
kubectl rollout undo deployment/ingenio-backend -n ingenio

# 回滚到特定版本
kubectl rollout undo deployment/ingenio-backend \
  --to-revision=2 \
  -n ingenio

# 查看回滚状态
kubectl rollout status deployment/ingenio-backend -n ingenio
```

### 9.4 金丝雀发布

创建 `backend-canary-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ingenio-backend-canary
  namespace: ingenio
spec:
  replicas: 1  # 仅启动1个金丝雀Pod
  selector:
    matchLabels:
      app: ingenio-backend
      version: canary
  template:
    metadata:
      labels:
        app: ingenio-backend
        version: canary
    spec:
      containers:
      - name: backend
        image: ingenio-backend:v1.1.0-canary  # 新版本
        # ... 其他配置与主Deployment相同
```

Service选择器会同时匹配稳定版和金丝雀版:

```yaml
selector:
  app: ingenio-backend  # 匹配所有版本
```

流量分配:
- 稳定版: 3个Pod (75%流量)
- 金丝雀版: 1个Pod (25%流量)

---

## 10. 监控和告警

### 10.1 安装Prometheus和Grafana

```bash
# 添加Helm仓库
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# 安装kube-prometheus-stack (Prometheus + Grafana)
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword=admin

# 查看服务
kubectl get svc -n monitoring
```

### 10.2 ServiceMonitor配置

创建 `servicemonitor.yaml`:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: ingenio-backend-monitor
  namespace: ingenio
  labels:
    app: ingenio-backend
spec:
  selector:
    matchLabels:
      app: ingenio-backend
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
    scrapeTimeout: 10s
```

### 10.3 PrometheusRule告警规则

创建 `prometheus-rules.yaml`:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: ingenio-backend-alerts
  namespace: ingenio
spec:
  groups:
  - name: ingenio-backend
    interval: 30s
    rules:
    # Pod不健康告警
    - alert: PodNotHealthy
      expr: kube_pod_status_phase{namespace="ingenio",pod=~"ingenio-backend.*",phase!="Running"} == 1
      for: 5m
      labels:
        severity: critical
      annotations:
        summary: "Pod {{ $labels.pod }} is not running"
        description: "Pod {{ $labels.pod }} has been in {{ $labels.phase }} state for more than 5 minutes."

    # 高错误率告警
    - alert: HighErrorRate
      expr: rate(http_server_requests_seconds_count{status=~"5..",namespace="ingenio"}[5m]) > 0.1
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "High error rate detected"
        description: "Error rate is {{ $value }} requests/second."

    # 高内存使用告警
    - alert: HighMemoryUsage
      expr: container_memory_usage_bytes{namespace="ingenio",pod=~"ingenio-backend.*"} / container_spec_memory_limit_bytes{namespace="ingenio",pod=~"ingenio-backend.*"} > 0.9
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "High memory usage on {{ $labels.pod }}"
        description: "Memory usage is {{ $value | humanizePercentage }}."

    # 数据库连接池耗尽
    - alert: DatabaseConnectionPoolExhausted
      expr: hikaricp_connections_active{namespace="ingenio"} / hikaricp_connections_max{namespace="ingenio"} > 0.9
      for: 2m
      labels:
        severity: critical
      annotations:
        summary: "Database connection pool nearly exhausted"
        description: "Connection pool usage is {{ $value | humanizePercentage }}."
```

### 10.4 Grafana Dashboard导入

1. 访问Grafana (默认端口3000)
2. 登录 (admin/admin)
3. 导入Dashboard:
   - JVM Dashboard: ID `4701`
   - Spring Boot Dashboard: ID `12900`
   - Kubernetes Cluster: ID `7249`

---

## 11. 日志聚合

### 11.1 安装EFK Stack

```bash
# 安装Elasticsearch
helm repo add elastic https://helm.elastic.co
helm install elasticsearch elastic/elasticsearch \
  --namespace logging \
  --create-namespace \
  --set replicas=3 \
  --set volumeClaimTemplate.resources.requests.storage=100Gi

# 安装Fluentd
helm install fluentd elastic/fluentd \
  --namespace logging \
  --set elasticsearch.host=elasticsearch-master

# 安装Kibana
helm install kibana elastic/kibana \
  --namespace logging \
  --set elasticsearch.hosts[0]=http://elasticsearch-master:9200
```

### 11.2 Fluentd配置

创建 `fluentd-configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluentd-config
  namespace: logging
data:
  fluent.conf: |-
    <source>
      @type tail
      path /var/log/containers/ingenio-backend*.log
      pos_file /var/log/fluentd-ingenio.log.pos
      tag kubernetes.ingenio.*
      <parse>
        @type json
        time_key time
        time_format %Y-%m-%dT%H:%M:%S.%NZ
      </parse>
    </source>

    <filter kubernetes.ingenio.**>
      @type kubernetes_metadata
      @id filter_kube_metadata
    </filter>

    <match kubernetes.ingenio.**>
      @type elasticsearch
      host elasticsearch-master
      port 9200
      logstash_format true
      logstash_prefix ingenio
      <buffer>
        flush_interval 10s
      </buffer>
    </match>
```

---

## 12. 备份和恢复

### 12.1 Velero安装（K8s备份工具）

```bash
# 安装Velero CLI
wget https://github.com/vmware-tanzu/velero/releases/download/v1.12.0/velero-v1.12.0-linux-amd64.tar.gz
tar -xvf velero-v1.12.0-linux-amd64.tar.gz
sudo mv velero-v1.12.0-linux-amd64/velero /usr/local/bin/

# 安装Velero服务端（使用MinIO作为存储）
velero install \
  --provider aws \
  --plugins velero/velero-plugin-for-aws:v1.8.0 \
  --bucket velero-backup \
  --secret-file ./credentials-velero \
  --use-volume-snapshots=false \
  --backup-location-config region=minio,s3ForcePathStyle="true",s3Url=http://minio.ingenio.svc:9000
```

### 12.2 创建备份

```bash
# 备份整个命名空间
velero backup create ingenio-backup-$(date +%Y%m%d) \
  --include-namespaces ingenio

# 备份特定资源
velero backup create ingenio-db-backup \
  --include-namespaces ingenio \
  --include-resources persistentvolumeclaims,persistentvolumes

# 定时备份
velero schedule create ingenio-daily \
  --schedule="0 2 * * *" \
  --include-namespaces ingenio
```

### 12.3 恢复

```bash
# 查看备份
velero backup get

# 恢复备份
velero restore create --from-backup ingenio-backup-20250115

# 查看恢复状态
velero restore get
velero restore describe <restore-name>
```

---

## 13. 故障排查

### 13.1 常用诊断命令

```bash
# 查看Pod状态
kubectl get pods -n ingenio

# 查看Pod详情
kubectl describe pod <pod-name> -n ingenio

# 查看Pod日志
kubectl logs <pod-name> -n ingenio
kubectl logs -f <pod-name> -n ingenio --tail=100

# 查看前一个容器的日志（崩溃后）
kubectl logs <pod-name> -n ingenio --previous

# 进入Pod调试
kubectl exec -it <pod-name> -n ingenio -- sh

# 查看事件
kubectl get events -n ingenio --sort-by='.lastTimestamp'

# 查看资源使用
kubectl top pods -n ingenio
kubectl top nodes
```

### 13.2 常见问题

#### 问题1: Pod处于Pending状态

**排查**:
```bash
kubectl describe pod <pod-name> -n ingenio
```

**常见原因**:
- 资源不足（CPU/内存）
- PVC未绑定
- 节点污点限制

**解决**:
```bash
# 查看节点资源
kubectl describe nodes

# 增加节点或调整资源请求
```

#### 问题2: Pod处于CrashLoopBackOff

**排查**:
```bash
kubectl logs <pod-name> -n ingenio --previous
kubectl describe pod <pod-name> -n ingenio
```

**常见原因**:
- 应用启动失败
- 健康检查失败
- 配置错误

#### 问题3: Service无法访问

**排查**:
```bash
# 查看Service
kubectl get svc -n ingenio
kubectl describe svc backend-svc -n ingenio

# 查看Endpoints
kubectl get endpoints backend-svc -n ingenio

# 测试Service DNS
kubectl run test-pod --rm -it --image=busybox -- nslookup backend-svc.ingenio.svc.cluster.local
```

---

## 附录

### A. 完整部署脚本

创建 `deploy.sh`:

```bash
#!/bin/bash
set -e

NAMESPACE="ingenio"

echo "开始部署Ingenio到Kubernetes..."

# 1. 创建命名空间
echo "创建命名空间..."
kubectl apply -f namespace.yaml

# 2. 创建ConfigMap和Secret
echo "创建配置..."
kubectl apply -f configmap.yaml
kubectl apply -f secrets.yaml

# 3. 部署StatefulSet
echo "部署数据库和缓存..."
kubectl apply -f postgres-statefulset.yaml
kubectl apply -f redis-statefulset.yaml
kubectl apply -f minio-statefulset.yaml

# 等待StatefulSet就绪
echo "等待StatefulSet就绪..."
kubectl wait --for=condition=ready pod -l app=postgres -n $NAMESPACE --timeout=300s
kubectl wait --for=condition=ready pod -l app=redis -n $NAMESPACE --timeout=300s
kubectl wait --for=condition=ready pod -l app=minio -n $NAMESPACE --timeout=300s

# 4. 部署Backend
echo "部署Backend应用..."
kubectl apply -f backend-deployment.yaml
kubectl apply -f backend-service.yaml

# 5. 部署Ingress
echo "部署Ingress..."
kubectl apply -f ingress.yaml

# 6. 部署HPA
echo "配置自动扩展..."
kubectl apply -f hpa.yaml

# 7. 部署监控
echo "部署监控配置..."
kubectl apply -f servicemonitor.yaml
kubectl apply -f prometheus-rules.yaml

echo "✅ 部署完成！"

# 查看部署状态
kubectl get all -n $NAMESPACE
```

### B. K8s命令速查

| 命令 | 说明 |
|-----|------|
| `kubectl get all -n ingenio` | 查看所有资源 |
| `kubectl logs -f <pod> -n ingenio` | 查看实时日志 |
| `kubectl exec -it <pod> -n ingenio -- sh` | 进入Pod |
| `kubectl scale deployment ingenio-backend --replicas=5 -n ingenio` | 手动扩容 |
| `kubectl rollout restart deployment ingenio-backend -n ingenio` | 重启Deployment |
| `kubectl delete pod <pod> -n ingenio` | 删除Pod（会自动重建） |

---

**文档版本**: v1.0
**最后更新**: 2025-01-15
**维护人**: Ingenio DevOps Team
