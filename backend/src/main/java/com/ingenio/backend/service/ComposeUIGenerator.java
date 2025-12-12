package com.ingenio.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Compose Multiplatform UI代码生成器
 *
 * 核心功能：
 * 1. 生成列表界面（Screen）
 * 2. 生成详情界面
 * 3. 生成表单界面（创建/编辑）
 * 4. 生成卡片组件
 * 5. 生成导航配置
 *
 * UI框架：Jetpack Compose + Compose Multiplatform
 * 设计规范：Material 3 Design
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComposeUIGenerator {

    /**
     * 生成列表界面（Screen）
     *
     * @param entity 实体定义
     * @return Kotlin Compose代码
     */
    public String generateListScreen(Map<String, Object> entity) {
        String tableName = (String) entity.get("tableName");
        String className = toCamelCase(tableName);
        String displayName = (String) entity.getOrDefault("displayName", className);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attributes =
                (List<Map<String, Object>>) entity.get("attributes");

        // 找出主要显示字段（前3个非ID字段）
        String primaryField = findPrimaryField(attributes);
        String secondaryField = findSecondaryField(attributes);

        String code = """
            package com.ingenio.generated.presentation.screen

            import androidx.compose.foundation.clickable
            import androidx.compose.foundation.layout.*
            import androidx.compose.foundation.lazy.LazyColumn
            import androidx.compose.foundation.lazy.items
            import androidx.compose.material.icons.Icons
            import androidx.compose.material.icons.filled.Add
            import androidx.compose.material.icons.filled.Delete
            import androidx.compose.material.icons.filled.Edit
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp
            import com.ingenio.generated.data.model.%s
            import com.ingenio.generated.presentation.viewmodel.%sViewModel

            /**
             * %s列表界面
             * 展示所有%s，支持下拉刷新、分页加载、CRUD操作
             */
            @OptIn(ExperimentalMaterial3Api::class)
            @Composable
            fun %sListScreen(
                viewModel: %sViewModel,
                onItemClick: (String) -> Unit = {},
                onCreateClick: () -> Unit = {}
            ) {
                val items by viewModel.items.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val error by viewModel.error.collectAsState()

                var showDeleteDialog by remember { mutableStateOf<String?>(null) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("%s") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = onCreateClick
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加%s")
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when {
                            isLoading && items.isEmpty() -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            error != null -> {
                                ErrorView(
                                    error = error,
                                    onRetry = { viewModel.refresh() },
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            items.isEmpty() -> {
                                EmptyView(
                                    message = "暂无数据",
                                    onAddClick = onCreateClick,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(items, key = { it.id ?: "" }) { item ->
                                        %sCard(
                                            item = item,
                                            onClick = { onItemClick(item.id ?: "") },
                                            onEdit = { onItemClick(item.id ?: "") },
                                            onDelete = { showDeleteDialog = item.id }
                                        )
                                    }

                                    // 加载更多指示器
                                    if (isLoading && items.isNotEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    // 触底加载更多
                                    item {
                                        LaunchedEffect(Unit) {
                                            viewModel.loadNextPage()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 删除确认对话框
                showDeleteDialog?.let { itemId ->
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = null },
                        title = { Text("确认删除") },
                        text = { Text("确定要删除这条记录吗？此操作不可撤销。") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.delete(itemId)
                                    showDeleteDialog = null
                                }
                            ) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = null }) {
                                Text("取消")
                            }
                        }
                    )
                }
            }

            /**
             * %s卡片组件
             */
            @Composable
            private fun %sCard(
                item: %s,
                onClick: () -> Unit,
                onEdit: () -> Unit,
                onDelete: () -> Unit,
                modifier: Modifier = Modifier
            ) {
                Card(
                    modifier = modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.%s?.toString() ?: "未命名",
                                style = MaterialTheme.typography.titleMedium
                            )
                            item.%s?.let { secondary ->
                                Text(
                                    text = secondary.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = onEdit) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onDelete) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            /**
             * 空状态视图
             */
            @Composable
            private fun EmptyView(
                message: String,
                onAddClick: () -> Unit,
                modifier: Modifier = Modifier
            ) {
                Column(
                    modifier = modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("添加第一条记录")
                    }
                }
            }

            /**
             * 错误视图
             */
            @Composable
            private fun ErrorView(
                error: String?,
                onRetry: () -> Unit,
                modifier: Modifier = Modifier
            ) {
                Column(
                    modifier = modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = error ?: "加载失败",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
            """.formatted(
                className, className,              // imports
                displayName, className,            // comments
                className, className,              // function parameters
                displayName,                       // TopAppBar title
                className,                         // FAB content description
                className,                         // Card function name
                displayName, className, className, // Card function parameters
                primaryField, secondaryField       // Card content fields
        );

        log.debug("生成列表界面: {}ListScreen", className);
        return code;
    }

    /**
     * 生成表单界面（创建/编辑）
     *
     * @param entity 实体定义
     * @return Kotlin Compose代码
     */
    public String generateFormScreen(Map<String, Object> entity) {
        String tableName = (String) entity.get("tableName");
        String className = toCamelCase(tableName);
        String displayName = (String) entity.getOrDefault("displayName", className);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attributes =
                (List<Map<String, Object>>) entity.get("attributes");

        StringBuilder formFields = new StringBuilder();
        StringBuilder stateDeclarations = new StringBuilder();

        for (Map<String, Object> attr : attributes) {
            String name = (String) attr.get("name");
            String sqlType = (String) attr.get("type");

            // 跳过ID和时间戳字段（自动生成）
            if ("id".equals(name) || name.contains("created") || name.contains("updated")) {
                continue;
            }

            String kotlinType = mapSqlTypeToKotlin(sqlType);
            stateDeclarations.append("    var ").append(name)
                    .append(" by remember { mutableStateOf(")
                    .append(getDefaultValue(kotlinType))
                    .append(") }\n");

            formFields.append(generateFormField(name, kotlinType, attr));
        }

        String code = """
            package com.ingenio.generated.presentation.screen

            import androidx.compose.foundation.layout.*
            import androidx.compose.foundation.rememberScrollState
            import androidx.compose.foundation.verticalScroll
            import androidx.compose.material.icons.Icons
            import androidx.compose.material.icons.filled.ArrowBack
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp
            import com.ingenio.generated.data.model.%s
            import com.ingenio.generated.presentation.viewmodel.%sViewModel

            /**
             * %s表单界面
             * 用于创建或编辑%s
             */
            @OptIn(ExperimentalMaterial3Api::class)
            @Composable
            fun %sFormScreen(
                viewModel: %sViewModel,
                itemId: String? = null,
                onBack: () -> Unit
            ) {
                // 状态管理
            %s

                // 如果是编辑模式，加载现有数据
                LaunchedEffect(itemId) {
                    itemId?.let { id ->
                        // TODO: 加载现有数据并填充表单
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (itemId == null) "新建%s" else "编辑%s") },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 表单字段
            %s

                        Spacer(modifier = Modifier.height(16.dp))

                        // 提交按钮
                        Button(
                            onClick = {
                                val item = %s(
                                    // TODO: 填充字段值
                                )
                                if (itemId == null) {
                                    viewModel.create(item)
                                } else {
                                    viewModel.update(itemId, item)
                                }
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (itemId == null) "创建" else "保存")
                        }
                    }
                }
            }
            """.formatted(
                className, className,             // imports
                displayName, className,           // comments
                className, className,             // function parameters
                stateDeclarations.toString(),     // state declarations
                displayName, displayName,         // TopAppBar titles
                formFields.toString(),            // form fields
                className                         // item construction
        );

        log.debug("生成表单界面: {}FormScreen", className);
        return code;
    }

    /**
     * 生成导航配置
     *
     * @param entities 所有实体定义
     * @return Kotlin导航代码
     */
    public String generateNavigation(List<Map<String, Object>> entities) {
        StringBuilder routes = new StringBuilder();
        StringBuilder navGraph = new StringBuilder();

        for (Map<String, Object> entity : entities) {
            String tableName = (String) entity.get("tableName");
            String className = toCamelCase(tableName);

            routes.append("    const val ").append(tableName.toUpperCase())
                    .append("_LIST = \"").append(tableName).append("_list\"\n");
            routes.append("    const val ").append(tableName.toUpperCase())
                    .append("_FORM = \"").append(tableName).append("_form/{itemId}\"\n");

            navGraph.append("        composable(Routes.").append(tableName.toUpperCase())
                    .append("_LIST) {\n");
            navGraph.append("            ").append(className).append("ListScreen(\n");
            navGraph.append("                viewModel = viewModel(),\n");
            navGraph.append("                onItemClick = { itemId -> navController.navigate(\"")
                    .append(tableName).append("_form/$itemId\") },\n");
            navGraph.append("                onCreateClick = { navController.navigate(\"")
                    .append(tableName).append("_form/new\") }\n");
            navGraph.append("            )\n");
            navGraph.append("        }\n\n");

            navGraph.append("        composable(Routes.").append(tableName.toUpperCase())
                    .append("_FORM) { backStackEntry ->\n");
            navGraph.append("            val itemId = backStackEntry.arguments?.getString(\"itemId\")\n");
            navGraph.append("            ").append(className).append("FormScreen(\n");
            navGraph.append("                viewModel = viewModel(),\n");
            navGraph.append("                itemId = if (itemId == \"new\") null else itemId,\n");
            navGraph.append("                onBack = { navController.popBackStack() }\n");
            navGraph.append("            )\n");
            navGraph.append("        }\n\n");
        }

        return """
            package com.ingenio.generated.presentation.navigation

            import androidx.compose.runtime.Composable
            import androidx.navigation.NavHostController
            import androidx.navigation.compose.NavHost
            import androidx.navigation.compose.composable
            import androidx.lifecycle.viewmodel.compose.viewModel

            /**
             * 路由定义
             */
            object Routes {
            %s
            }

            /**
             * 导航图配置
             */
            @Composable
            fun AppNavigation(navController: NavHostController) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.values().first()
                ) {
            %s
                }
            }
            """.formatted(routes.toString(), navGraph.toString());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成单个表单字段
     */
    private String generateFormField(String name, String kotlinType, Map<String, Object> attr) {
        String label = (String) attr.getOrDefault("comment", name);

        if ("String".equals(kotlinType)) {
            return String.format("""
                            OutlinedTextField(
                                value = %s,
                                onValueChange = { %s = it },
                                label = { Text("%s") },
                                modifier = Modifier.fillMaxWidth()
                            )

                        """, name, name, label);
        } else if ("Int".equals(kotlinType) || "Long".equals(kotlinType)) {
            return String.format("""
                            OutlinedTextField(
                                value = %s.toString(),
                                onValueChange = { %s = it.toIntOrNull() ?: 0 },
                                label = { Text("%s") },
                                modifier = Modifier.fillMaxWidth()
                            )

                        """, name, name, label);
        } else if ("Boolean".equals(kotlinType)) {
            return String.format("""
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("%s")
                                Switch(
                                    checked = %s,
                                    onCheckedChange = { %s = it }
                                )
                            }

                        """, label, name, name);
        }

        // 默认文本输入
        return String.format("""
                        OutlinedTextField(
                            value = %s.toString(),
                            onValueChange = { %s = it },
                            label = { Text("%s") },
                            modifier = Modifier.fillMaxWidth()
                        )

                    """, name, name, label);
    }

    /**
     * 获取Kotlin类型的默认值
     */
    private String getDefaultValue(String kotlinType) {
        return switch (kotlinType) {
            case "String" -> "\"\"";
            case "Int", "Long", "Short" -> "0";
            case "Double", "Float" -> "0.0";
            case "Boolean" -> "false";
            default -> "\"\"";
        };
    }

    /**
     * 查找主要显示字段（第一个非ID字段）
     */
    private String findPrimaryField(List<Map<String, Object>> attributes) {
        for (Map<String, Object> attr : attributes) {
            String name = (String) attr.get("name");
            if (!"id".equals(name) && !name.contains("created") && !name.contains("updated")) {
                return name;
            }
        }
        return "id";
    }

    /**
     * 查找次要显示字段（第二个非ID字段）
     */
    private String findSecondaryField(List<Map<String, Object>> attributes) {
        boolean foundPrimary = false;
        for (Map<String, Object> attr : attributes) {
            String name = (String) attr.get("name");
            if (!"id".equals(name) && !name.contains("created") && !name.contains("updated")) {
                if (foundPrimary) {
                    return name;
                }
                foundPrimary = true;
            }
        }
        return null;
    }

    /**
     * 将下划线命名转换为驼峰命名
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
     * SQL类型映射到Kotlin类型（与KotlinMultiplatformGenerator保持一致）
     */
    private String mapSqlTypeToKotlin(String sqlType) {
        String upperType = sqlType.toUpperCase();

        if (upperType.contains("BIGINT") || upperType.contains("INT8")) return "Long";
        if (upperType.contains("INTEGER") || upperType.contains("INT") || upperType.contains("SERIAL")) return "Int";
        if (upperType.contains("SMALLINT") || upperType.contains("INT2")) return "Short";
        if (upperType.contains("DOUBLE") || upperType.contains("FLOAT8")) return "Double";
        if (upperType.contains("REAL") || upperType.contains("FLOAT4")) return "Float";
        if (upperType.contains("NUMERIC") || upperType.contains("DECIMAL")) return "Double";
        if (upperType.contains("VARCHAR") || upperType.contains("TEXT") || upperType.contains("CHAR")) return "String";
        if (upperType.contains("BOOLEAN") || upperType.contains("BOOL")) return "Boolean";
        if (upperType.contains("TIMESTAMP") || upperType.contains("DATE") || upperType.contains("TIME")) return "LocalDateTime";
        if (upperType.contains("UUID")) return "String";

        return "String"; // 默认
    }
}
