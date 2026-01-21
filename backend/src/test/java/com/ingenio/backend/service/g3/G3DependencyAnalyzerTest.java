package com.ingenio.backend.service.g3;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G3DependencyAnalyzer 单元测试
 *
 * 目标：
 * - 验证能从 Schema SQL 中提取表并构建依赖图
 * - 验证拓扑排序会遵守外键依赖与“层级生成顺序”
 */
class G3DependencyAnalyzerTest {

    @Test
    void analyzeFromSchema_shouldRespectForeignKeyOrder() {
        G3DependencyAnalyzer analyzer = new G3DependencyAnalyzer();

        String schemaSql = """
                CREATE TABLE users (
                  id UUID PRIMARY KEY
                );

                CREATE TABLE orders (
                  id UUID PRIMARY KEY,
                  user_id UUID NOT NULL REFERENCES users(id)
                );
                """;

        G3TaskDependencyGraph graph = analyzer.analyzeFromSchema(schemaSql);

        // 两张表，每张表默认拆 4 个任务：entity/mapper/service/controller
        assertEquals(8, graph.getNodes().size());

        List<G3TaskDependencyGraph.G3TaskNode> sorted = analyzer.topologicalSort(graph);

        int usersEntity = indexOf(sorted, "task_entity_users");
        int ordersEntity = indexOf(sorted, "task_entity_orders");
        assertTrue(usersEntity >= 0 && ordersEntity >= 0);
        assertTrue(usersEntity < ordersEntity, "外键被引用表 users 应先于 orders 生成");

        // 同一张表内：Entity -> Mapper -> Service -> Controller
        assertTrue(indexOf(sorted, "task_entity_users") < indexOf(sorted, "task_mapper_users"));
        assertTrue(indexOf(sorted, "task_mapper_users") < indexOf(sorted, "task_service_users"));
        assertTrue(indexOf(sorted, "task_service_users") < indexOf(sorted, "task_controller_users"));
    }

    @Test
    void analyzeFromSchema_shouldReturnEmptyGraph_whenSchemaBlank() {
        G3DependencyAnalyzer analyzer = new G3DependencyAnalyzer();
        G3TaskDependencyGraph graph = analyzer.analyzeFromSchema("   ");
        assertEquals(0, graph.getNodes().size());
        assertEquals(0, graph.getEdges().size());
    }

    private int indexOf(List<G3TaskDependencyGraph.G3TaskNode> nodes, String id) {
        for (int i = 0; i < nodes.size(); i++) {
            if (id.equals(nodes.get(i).getId())) return i;
        }
        return -1;
    }
}

