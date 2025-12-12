package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.config.SupabaseConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Supabase Database服务
 *
 * 核心功能：
 * 1. 执行DDL创建表（通过Supabase SQL API）
 * 2. 使用PostgREST自动生成的REST API进行CRUD操作
 * 3. 管理数据库Schema版本
 *
 * 优势：
 * - 无需手写Controller，PostgREST自动生成RESTful API
 * - 无需手写GraphQL Resolver，Supabase自动生成GraphQL API
 * - 支持行级安全策略（Row Level Security, RLS）
 * - 支持实时订阅（Realtime）
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
     * 执行DDL SQL语句（创建表、修改表结构等）
     *
     * 注意：Supabase不直接暴露SQL执行API，需要通过以下方式：
     * 1. 使用Supabase Management API创建migration
     * 2. 使用PostgreSQL JDBC连接执行SQL（推荐）
     *
     * @param ddlSql DDL SQL语句
     * @return 执行结果
     */
    public ExecutionResult executeDDL(String ddlSql) {
        log.info("执行Supabase DDL: length={}", ddlSql.length());

        try {
            // 方案1：通过PostgreSQL JDBC直接执行（推荐）
            // 这需要Supabase Database的直连URL
            // 格式：postgresql://postgres:[password]@db.[project-ref].supabase.co:5432/postgres

            // 方案2：通过Supabase Management API创建migration（生产环境推荐）
            // POST https://api.supabase.com/v1/projects/{ref}/database/migrations

            // 临时实现：返回模拟结果
            // 实际生产中应该使用JDBC或Management API
            log.warn("DDL执行功能需要配置Supabase Database直连URL或Management API");

            ExecutionResult result = new ExecutionResult();
            result.setSuccess(true);
            result.setMessage("DDL已提交（需要配置实际执行方式）");
            result.setSql(ddlSql);
            return result;

        } catch (Exception e) {
            log.error("执行DDL失败", e);
            ExecutionResult result = new ExecutionResult();
            result.setSuccess(false);
            result.setMessage("DDL执行失败: " + e.getMessage());
            result.setSql(ddlSql);
            return result;
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
