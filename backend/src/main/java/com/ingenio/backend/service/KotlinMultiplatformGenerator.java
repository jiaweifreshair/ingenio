package com.ingenio.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Kotlin Multiplatform代码生成器
 *
 * 核心功能：
 * 1. 生成数据模型（data class）
 * 2. 生成Repository层（Supabase客户端集成）
 * 3. 生成ViewModel层（业务逻辑）
 * 4. SQL类型映射到Kotlin类型
 *
 * 支持平台：Android、iOS、Web（通过Compose Multiplatform）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KotlinMultiplatformGenerator {

    /**
     * 生成Kotlin数据模型（data class）
     *
     * @param entity 实体定义（包含tableName和attributes）
     * @return Kotlin代码字符串
     */
    public String generateDataModel(Map<String, Object> entity) {
        String tableName = (String) entity.get("tableName");
        String className = toCamelCase(tableName);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attributes =
                (List<Map<String, Object>>) entity.get("attributes");

        StringBuilder code = new StringBuilder();
        code.append("package com.ingenio.generated.data.model\n\n");
        code.append("import kotlinx.serialization.Serializable\n");
        code.append("import kotlinx.datetime.LocalDateTime\n\n");
        code.append("/**\n");
        code.append(" * ").append(className).append(" 数据模型\n");
        code.append(" * 表名: ").append(tableName).append("\n");
        code.append(" */\n");
        code.append("@Serializable\n");
        code.append("data class ").append(className).append("(\n");

        for (int i = 0; i < attributes.size(); i++) {
            Map<String, Object> attr = attributes.get(i);
            String name = (String) attr.get("name");
            String sqlType = (String) attr.get("type");
            String kotlinType = mapSqlTypeToKotlin(sqlType);
            Boolean nullable = (Boolean) attr.getOrDefault("nullable", true);
            String comment = (String) attr.getOrDefault("comment", "");

            if (!comment.isEmpty()) {
                code.append("    /** ").append(comment).append(" */\n");
            }
            code.append("    val ").append(name).append(": ");
            code.append(kotlinType);
            if (nullable) {
                code.append("?");
            }
            if (i < attributes.size() - 1) {
                code.append(",");
            }
            code.append("\n");
        }

        code.append(")\n");

        log.debug("生成数据模型: {}", className);
        return code.toString();
    }

    /**
     * 生成Supabase Repository
     *
     * @param entity 实体定义
     * @return Kotlin Repository代码
     */
    public String generateRepository(Map<String, Object> entity) {
        String tableName = (String) entity.get("tableName");
        String className = toCamelCase(tableName);

        String code = """
            package com.ingenio.generated.data.repository

            import com.ingenio.generated.data.model.%s
            import io.github.jan.supabase.SupabaseClient
            import io.github.jan.supabase.postgrest.from
            import io.github.jan.supabase.postgrest.query.Columns

            /**
             * %s Repository
             * 基于Supabase PostgREST自动生成的CRUD接口
             */
            class %sRepository(
                private val supabase: SupabaseClient
            ) {

                /**
                 * 获取所有%s
                 */
                suspend fun getAll(): List<%s> {
                    return supabase.from("%s").select().decodeList()
                }

                /**
                 * 根据ID获取%s
                 */
                suspend fun getById(id: String): %s? {
                    return supabase.from("%s")
                        .select {
                            filter {
                                eq("id", id)
                            }
                        }
                        .decodeSingleOrNull()
                }

                /**
                 * 创建%s
                 */
                suspend fun create(item: %s): %s {
                    return supabase.from("%s")
                        .insert(item)
                        .decodeSingle()
                }

                /**
                 * 更新%s
                 */
                suspend fun update(id: String, item: %s): %s {
                    return supabase.from("%s")
                        .update(item) {
                            filter {
                                eq("id", id)
                            }
                        }
                        .decodeSingle()
                }

                /**
                 * 删除%s
                 */
                suspend fun delete(id: String) {
                    supabase.from("%s").delete {
                        filter {
                            eq("id", id)
                        }
                    }
                }

                /**
                 * 分页查询%s
                 */
                suspend fun getPage(page: Int, pageSize: Int = 20): List<%s> {
                    val start = page * pageSize
                    return supabase.from("%s")
                        .select {
                            range(start.toLong(), (start + pageSize - 1).toLong())
                        }
                        .decodeList()
                }
            }
            """.formatted(
                className, className, className, // package, comment, class name
                className, className, tableName,  // getAll
                className, className, tableName,  // getById
                className, className, className, tableName, // create
                className, className, className, tableName, // update
                className, tableName,             // delete
                className, className, tableName   // getPage
        );

        log.debug("生成Repository: {}Repository", className);
        return code;
    }

    /**
     * 生成ViewModel
     *
     * @param entity 实体定义
     * @return Kotlin ViewModel代码
     */
    public String generateViewModel(Map<String, Object> entity) {
        String tableName = (String) entity.get("tableName");
        String className = toCamelCase(tableName);

        String code = """
            package com.ingenio.generated.presentation.viewmodel

            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import com.ingenio.generated.data.model.%s
            import com.ingenio.generated.data.repository.%sRepository
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow
            import kotlinx.coroutines.flow.asStateFlow
            import kotlinx.coroutines.launch

            /**
             * %s ViewModel
             * 管理%s的业务逻辑和UI状态
             */
            class %sViewModel(
                private val repository: %sRepository
            ) : ViewModel() {

                private val _items = MutableStateFlow<List<%s>>(emptyList())
                val items: StateFlow<List<%s>> = _items.asStateFlow()

                private val _isLoading = MutableStateFlow(false)
                val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

                private val _error = MutableStateFlow<String?>(null)
                val error: StateFlow<String?> = _error.asStateFlow()

                private val _currentPage = MutableStateFlow(0)
                val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

                init {
                    loadItems()
                }

                /**
                 * 加载%s列表
                 */
                fun loadItems() {
                    viewModelScope.launch {
                        _isLoading.value = true
                        _error.value = null
                        try {
                            val result = repository.getAll()
                            _items.value = result
                        } catch (e: Exception) {
                            _error.value = "加载失败: ${'$'}{e.message}"
                        } finally {
                            _isLoading.value = false
                        }
                    }
                }

                /**
                 * 创建%s
                 */
                fun create(item: %s) {
                    viewModelScope.launch {
                        _isLoading.value = true
                        _error.value = null
                        try {
                            repository.create(item)
                            loadItems() // 重新加载列表
                        } catch (e: Exception) {
                            _error.value = "创建失败: ${'$'}{e.message}"
                        } finally {
                            _isLoading.value = false
                        }
                    }
                }

                /**
                 * 更新%s
                 */
                fun update(id: String, item: %s) {
                    viewModelScope.launch {
                        _isLoading.value = true
                        _error.value = null
                        try {
                            repository.update(id, item)
                            loadItems() // 重新加载列表
                        } catch (e: Exception) {
                            _error.value = "更新失败: ${'$'}{e.message}"
                        } finally {
                            _isLoading.value = false
                        }
                    }
                }

                /**
                 * 删除%s
                 */
                fun delete(id: String) {
                    viewModelScope.launch {
                        _isLoading.value = true
                        _error.value = null
                        try {
                            repository.delete(id)
                            loadItems() // 重新加载列表
                        } catch (e: Exception) {
                            _error.value = "删除失败: ${'$'}{e.message}"
                        } finally {
                            _isLoading.value = false
                        }
                    }
                }

                /**
                 * 加载下一页
                 */
                fun loadNextPage() {
                    viewModelScope.launch {
                        _isLoading.value = true
                        _error.value = null
                        try {
                            val nextPage = _currentPage.value + 1
                            val result = repository.getPage(nextPage)
                            _items.value = _items.value + result
                            _currentPage.value = nextPage
                        } catch (e: Exception) {
                            _error.value = "加载失败: ${'$'}{e.message}"
                        } finally {
                            _isLoading.value = false
                        }
                    }
                }

                /**
                 * 刷新列表
                 */
                fun refresh() {
                    _currentPage.value = 0
                    loadItems()
                }
            }
            """.formatted(
                className, className,          // imports
                className, className,          // comments
                className, className,          // class name
                className, className,          // items state
                className,                     // loadItems comment
                className, className,          // create
                className, className,          // update
                className                      // delete
        );

        log.debug("生成ViewModel: {}ViewModel", className);
        return code;
    }

    /**
     * 生成完整的KMP项目结构配置
     *
     * @param entities 所有实体定义
     * @return build.gradle.kts配置
     */
    public String generateBuildConfig(List<Map<String, Object>> entities) {
        return """
            plugins {
                kotlin("multiplatform") version "1.9.20"
                kotlin("plugin.serialization") version "1.9.20"
                id("com.android.library")
                id("org.jetbrains.compose") version "1.5.10"
            }

            kotlin {
                androidTarget {
                    compilations.all {
                        kotlinOptions {
                            jvmTarget = "17"
                        }
                    }
                }

                listOf(
                    iosX64(),
                    iosArm64(),
                    iosSimulatorArm64()
                ).forEach {
                    it.binaries.framework {
                        baseName = "shared"
                        isStatic = true
                    }
                }

                sourceSets {
                    val commonMain by getting {
                        dependencies {
                            // Kotlin Multiplatform
                            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

                            // Supabase客户端
                            implementation("io.github.jan-tennert.supabase:postgrest-kt:2.0.0")
                            implementation("io.github.jan-tennert.supabase:realtime-kt:2.0.0")
                            implementation("io.github.jan-tennert.supabase:storage-kt:2.0.0")

                            // Compose Multiplatform
                            implementation(compose.runtime)
                            implementation(compose.foundation)
                            implementation(compose.material3)
                            implementation(compose.ui)

                            // ViewModel
                            implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
                        }
                    }

                    val androidMain by getting {
                        dependencies {
                            implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
                        }
                    }

                    val iosMain by creating {
                        dependsOn(commonMain)
                    }
                }
            }

            android {
                namespace = "com.ingenio.generated"
                compileSdk = 34

                defaultConfig {
                    minSdk = 24
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            """;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将下划线命名转换为驼峰命名
     *
     * 示例: user_profile → UserProfile
     */
    private String toCamelCase(String snakeCase) {
        String[] parts = snakeCase.split("_");
        StringBuilder camelCase = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                camelCase.append(Character.toUpperCase(part.charAt(0)));
                camelCase.append(part.substring(1).toLowerCase());
            }
        }

        return camelCase.toString();
    }

    /**
     * SQL类型映射到Kotlin类型
     *
     * @param sqlType SQL数据类型
     * @return Kotlin类型字符串
     */
    private String mapSqlTypeToKotlin(String sqlType) {
        String upperType = sqlType.toUpperCase();

        // 整数类型
        if (upperType.contains("BIGINT") || upperType.contains("INT8")) {
            return "Long";
        }
        if (upperType.contains("INTEGER") || upperType.contains("INT") ||
                upperType.contains("SERIAL")) {
            return "Int";
        }
        if (upperType.contains("SMALLINT") || upperType.contains("INT2")) {
            return "Short";
        }

        // 浮点类型
        if (upperType.contains("DOUBLE") || upperType.contains("FLOAT8")) {
            return "Double";
        }
        if (upperType.contains("REAL") || upperType.contains("FLOAT4")) {
            return "Float";
        }
        if (upperType.contains("NUMERIC") || upperType.contains("DECIMAL")) {
            return "Double";
        }

        // 字符串类型
        if (upperType.contains("VARCHAR") || upperType.contains("TEXT") ||
                upperType.contains("CHAR")) {
            return "String";
        }

        // 布尔类型
        if (upperType.contains("BOOLEAN") || upperType.contains("BOOL")) {
            return "Boolean";
        }

        // 时间类型
        if (upperType.contains("TIMESTAMP")) {
            return "LocalDateTime";
        }
        if (upperType.contains("DATE")) {
            return "LocalDate";
        }
        if (upperType.contains("TIME")) {
            return "LocalTime";
        }

        // UUID类型
        if (upperType.contains("UUID")) {
            return "String"; // Kotlin中用String表示UUID
        }

        // JSON类型
        if (upperType.contains("JSON") || upperType.contains("JSONB")) {
            return "Map<String, Any>"; // 或使用JsonElement
        }

        // 数组类型
        if (upperType.contains("ARRAY")) {
            return "List<String>"; // 默认String数组，可根据需要调整
        }

        // 默认String类型
        log.warn("未识别的SQL类型: {}，使用默认String类型", sqlType);
        return "String";
    }
}
