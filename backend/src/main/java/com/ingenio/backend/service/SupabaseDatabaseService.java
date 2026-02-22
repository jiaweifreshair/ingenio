package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.config.SupabaseConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Supabase Database服务（V2.0增强）
 *
 * 核心功能：
 * 1. 执行DDL创建表（通过JDBC直连 - V2.0激活）
 * 2. 使用PostgREST自动生成的REST API进行CRUD操作
 * 3. 管理数据库Schema版本
 *
 * V2.0增强：
 * - 激活DDL执行功能，通过JDBC直连Supabase PostgreSQL
 * - 支持执行CREATE TABLE、CREATE INDEX、RLS策略等
 *
 * 优势：
 * - 无需手写Controller，PostgREST自动生成RESTful API
 * - 无需手写GraphQL Resolver，Supabase自动生成GraphQL API
 * - 支持行级安全策略（Row Level Security, RLS）
 * - 支持实时订阅（Realtime）
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-01-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupabaseDatabaseService {

    private final SupabaseConfig supabaseConfig;
    private final ObjectMapper objectMapper;

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 执行DDL SQL语句（创建表、修改表结构等）- V2.0激活
     *
     * 实现方式：通过JDBC直连Supabase PostgreSQL执行DDL
     *
     * 支持的DDL类型：
     * - CREATE TABLE：创建表
     * - ALTER TABLE：修改表结构
     * - CREATE INDEX：创建索引
     * - CREATE POLICY：创建RLS策略
     * - GRANT/REVOKE：权限管理
     *
     * @param ddlSql DDL SQL语句
     * @return 执行结果
     */
    public ExecutionResult executeDDL(String ddlSql) {
        log.info("执行Supabase DDL: length={}", ddlSql.length());

        // 检查是否配置了DDL执行能力
        if (!supabaseConfig.canExecuteDdl()) {
            log.warn("DDL执行功能未配置：需要设置 SUPABASE_DIRECT_DATABASE_URL 或 SUPABASE_DB_PASSWORD");
            ExecutionResult result = new ExecutionResult();
            result.setSuccess(false);
            result.setMessage("DDL执行功能未配置，请设置 Supabase 数据库直连凭据");
            result.setSql(ddlSql);
            return result;
        }

        String jdbcUrl = supabaseConfig.getJdbcDirectUrl();
        if (!StringUtils.hasText(jdbcUrl)) {
            log.error("无法构建Supabase JDBC URL");
            ExecutionResult result = new ExecutionResult();
            result.setSuccess(false);
            result.setMessage("无法构建数据库连接URL，请检查配置");
            result.setSql(ddlSql);
            return result;
        }

        log.info("使用JDBC直连执行DDL: url={}", jdbcUrl.replaceAll("password=[^&]+", "password=***"));

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {

            // 执行DDL（可能包含多条语句，用分号分隔）
            String[] statements = ddlSql.split(";");
            int successCount = 0;
            StringBuilder errors = new StringBuilder();

            for (String sql : statements) {
                String trimmedSql = sql.trim();
                if (trimmedSql.isEmpty()) {
                    continue;
                }

                try {
                    stmt.execute(trimmedSql);
                    successCount++;
                    log.debug("DDL语句执行成功: {}", trimmedSql.substring(0, Math.min(100, trimmedSql.length())));
                } catch (SQLException e) {
                    // 记录错误但继续执行后续语句（表/索引可能已存在）
                    String errorMsg = e.getMessage();
                    log.warn("DDL语句执行警告: sql={}, error={}",
                            trimmedSql.substring(0, Math.min(50, trimmedSql.length())), errorMsg);

                    // 忽略"已存在"类型的错误
                    if (!isIgnorableError(errorMsg)) {
                        errors.append(errorMsg).append("; ");
                    } else {
                        successCount++; // 已存在也算成功
                    }
                }
            }

            ExecutionResult result = new ExecutionResult();
            if (errors.length() > 0) {
                result.setSuccess(false);
                result.setMessage(String.format("DDL部分执行成功 (%d/%d): %s",
                        successCount, statements.length, errors.toString()));
            } else {
                result.setSuccess(true);
                result.setMessage(String.format("DDL执行成功 (%d条语句)", successCount));
            }
            result.setSql(ddlSql);

            log.info("DDL执行完成: success={}, count={}", result.isSuccess(), successCount);
            return result;

        } catch (SQLException e) {
            log.error("DDL执行失败: {}", e.getMessage(), e);
            ExecutionResult result = new ExecutionResult();
            result.setSuccess(false);
            result.setMessage("DDL执行失败: " + e.getMessage());
            result.setSql(ddlSql);
            return result;
        }
    }

    /**
     * 判断是否为可忽略的错误（如"表已存在"）
     *
     * @param errorMessage 错误消息
     * @return true 如果是可忽略的错误
     */
    private boolean isIgnorableError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        String lower = errorMessage.toLowerCase();
        return lower.contains("already exists") ||
               lower.contains("duplicate") ||
               lower.contains("relation") && lower.contains("already");
    }

    /**
     * 测试数据库连接是否正常
     *
     * @return true 如果连接成功
     */
    public boolean testConnection() {
        if (!supabaseConfig.canExecuteDdl()) {
            log.warn("无法测试连接：DDL执行功能未配置");
            return false;
        }

        String jdbcUrl = supabaseConfig.getJdbcDirectUrl();
        if (!StringUtils.hasText(jdbcUrl)) {
            return false;
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            boolean valid = conn.isValid(5);
            log.info("Supabase数据库连接测试: {}", valid ? "成功" : "失败");
            return valid;
        } catch (SQLException e) {
            log.error("Supabase数据库连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 调用PostgREST自动生成的API进行CRUD操作
     *
     * PostgREST会自动为每个表生成RESTful端点：
     * - GET    /table_name           查询列表
     * - GET    /table_name?id=eq.1   查询单条
     * - POST   /table_name           创建
     * - PATCH  /table_name?id=eq.1   更新
     * - DELETE /table_name?id=eq.1   删除
     *
     * @param tableName 表名
     * @param method    HTTP方法
     * @param query     查询参数（可选）
     * @param body      请求体（可选）
     * @return API响应
     */
    public ApiResponse callPostgREST(String tableName, String method, String query, Map<String, Object> body) {
        log.info("调用PostgREST API: table={}, method={}", tableName, method);

        try {
            // 构造URL
            String url = supabaseConfig.getDatabaseUrl() + "/" + tableName;
            if (query != null && !query.isEmpty()) {
                url += "?" + query;
            }

            // 构造请求
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseConfig.getAnonKey())
                    .addHeader("Authorization", "Bearer " + supabaseConfig.getAnonKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation"); // 返回完整对象

            // 添加请求体
            if ("POST".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                String bodyJson = objectMapper.writeValueAsString(body);
                requestBuilder.method(method.toUpperCase(),
                        RequestBody.create(bodyJson, MediaType.parse("application/json")));
            } else {
                requestBuilder.method(method.toUpperCase(), null);
            }

            Request request = requestBuilder.build();

            // 执行请求
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "{}";

                ApiResponse apiResponse = new ApiResponse();
                apiResponse.setSuccess(response.isSuccessful());
                apiResponse.setStatusCode(response.code());

                if (response.isSuccessful()) {
                    apiResponse.setData(objectMapper.readValue(responseBody, JsonNode.class));
                } else {
                    apiResponse.setError(responseBody);
                }

                log.info("PostgREST API响应: statusCode={}, success={}",
                        response.code(), response.isSuccessful());

                return apiResponse;
            }

        } catch (Exception e) {
            log.error("调用PostgREST API失败: table={}", tableName, e);
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setSuccess(false);
            apiResponse.setError("API调用失败: " + e.getMessage());
            return apiResponse;
        }
    }

    /**
     * 查询表列表
     *
     * @param tableName 表名
     * @param query     查询条件（PostgREST语法）
     * @return 查询结果
     */
    public ApiResponse query(String tableName, String query) {
        return callPostgREST(tableName, "GET", query, null);
    }

    /**
     * 插入数据
     *
     * @param tableName 表名
     * @param data      数据
     * @return 插入结果
     */
    public ApiResponse insert(String tableName, Map<String, Object> data) {
        return callPostgREST(tableName, "POST", null, data);
    }

    /**
     * 更新数据
     *
     * @param tableName 表名
     * @param query     更新条件
     * @param data      更新数据
     * @return 更新结果
     */
    public ApiResponse update(String tableName, String query, Map<String, Object> data) {
        return callPostgREST(tableName, "PATCH", query, data);
    }

    /**
     * 删除数据
     *
     * @param tableName 表名
     * @param query     删除条件
     * @return 删除结果
     */
    public ApiResponse delete(String tableName, String query) {
        return callPostgREST(tableName, "DELETE", query, null);
    }

    /**
     * DDL执行结果
     */
    public static class ExecutionResult {
        private boolean success;
        private String message;
        private String sql;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }
    }

    /**
     * API响应
     */
    public static class ApiResponse {
        private boolean success;
        private int statusCode;
        private JsonNode data;
        private String error;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public JsonNode getData() {
            return data;
        }

        public void setData(JsonNode data) {
            this.data = data;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
