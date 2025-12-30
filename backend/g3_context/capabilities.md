# Ingenio (妙构) G3 引擎能力注册表 (Capabilities Registry)

本文档定义了 Ingenio 平台现有的、可供 G3 引擎（蓝方 Agent）调用的核心能力。蓝方 Agent 在生成代码时，应优先通过 `AgentClient` 或直接注入 Service 的方式调用这些能力，而不是重复实现。

## 1. 用户与租户 (Identity & Tenant)

- **服务名**: `UserService` / `TenantService`
- **核心功能**:
    - 用户注册/登录 (基于 Sa-Token)
    - 租户创建与隔离
    - 角色与权限校验 (RBAC)
- **调用契约**:
    ```java
    // 获取当前登录用户
    User currentUser = SecurityUtils.getLoginUser();
    
    // 获取当前租户ID
    String tenantId = TenantContext.getTenantId();
    ```

## 2. 支付中心 (Payment Center)

- **服务名**: `PaymentService`
- **核心功能**:
    - 创建支付订单 (Stripe/Alipay/WeChat)
    - 处理支付回调 (Webhook)
    - 查询订单状态
- **API 示例**:
    ```java
    PaymentResponse createOrder(PaymentRequest request);
    boolean verifyPayment(String orderId);
    ```

## 3. 消息通知 (Notification)

- **服务名**: `NotificationService`
- **核心功能**:
    - 发送邮件 (Email)
    - 发送短信 (SMS)
    - 站内信 (Inbox)
- **API 示例**:
    ```java
    void sendEmail(String to, String subject, String content);
    void sendSms(String phone, String templateCode, Map<String, String> params);
    ```

## 4. 存储服务 (Storage)

- **服务名**: `StorageService` (MinIO/S3)
- **核心功能**:
    - 文件上传
    - 文件下载/预览链接生成
- **API 示例**:
    ```java
    String uploadFile(MultipartFile file, String bucket);
    String getPresignedUrl(String objectName);
    ```

---

**注意**: 本文档由控制面维护，Agent 仅有读取权限。
