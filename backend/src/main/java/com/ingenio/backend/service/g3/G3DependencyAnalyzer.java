package com.ingenio.backend.service.g3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * G3依赖分析器
 *
 * 负责分析生成任务的依赖关系，构建DAG (有向无环图)
 * 确保代码按照 Entity -> Mapper -> Service -> Controller 的正确顺序生成
 *
 * @author Claude
 * @since 2025-01-14
 */
@Slf4j
@Service
public class G3DependencyAnalyzer {

    /**
     * 解析 CREATE TABLE 语句（包含表体），用于后续提取外键依赖。
     *
     * <p>说明：</p>
     * <ul>
     *   <li>这里不做完整 SQL 解析，仅覆盖 G3 生成的常见 DDL 形态；</li>
     *   <li>当无法匹配“表体”时，会回退到仅提取表名的模式。</li>
     * </ul>
     */
    private static final Pattern CREATE_TABLE_BLOCK_PATTERN = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:\"|`)?(?<name>[A-Za-z0-9_]+)(?:\"|`)?\\s*\\((?<body>[\\s\\S]*?)\\)\\s*;?",
            Pattern.CASE_INSENSITIVE);

    /**
     * 仅提取 CREATE TABLE 的表名（回退模式）。
     */
    private static final Pattern CREATE_TABLE_NAME_PATTERN = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:\"|`)?([A-Za-z0-9_]+)(?:\"|`)?",
            Pattern.CASE_INSENSITIVE);

    /**
     * 提取外键引用的表名（启发式）。
     */
    private static final Pattern REFERENCES_PATTERN = Pattern.compile(
            "REFERENCES\\s+([^\\s(]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Comparator<G3TaskDependencyGraph.G3TaskNode> DEFAULT_NODE_COMPARATOR =
            Comparator.comparingInt(G3TaskDependencyGraph.G3TaskNode::getPriority)
                    .thenComparing(G3TaskDependencyGraph.G3TaskNode::getType, Comparator.nullsLast(String::compareTo))
                    .thenComparing(G3TaskDependencyGraph.G3TaskNode::getRelatedEntity, Comparator.nullsLast(String::compareTo))
                    .thenComparing(G3TaskDependencyGraph.G3TaskNode::getName, Comparator.nullsLast(String::compareTo))
                    .thenComparing(G3TaskDependencyGraph.G3TaskNode::getId, Comparator.nullsLast(String::compareTo));

    /**
     * 根据数据库Schema SQL分析任务依赖
     *
     * @param schemaSql 数据库建表语句
     * @return 任务依赖图
     */
    public G3TaskDependencyGraph analyzeFromSchema(String schemaSql) {
        log.info("开始分析Schema依赖关系...");
        G3TaskDependencyGraph graph = new G3TaskDependencyGraph();
        
        if (schemaSql == null || schemaSql.isBlank()) {
            log.warn("Schema SQL为空，无法生成依赖图");
            return graph;
        }

        // 1) 提取表名与表体（优先），用于识别外键依赖
        Map<String, String> tableBodies = extractCreateTableBodies(schemaSql);
        List<String> tables = !tableBodies.isEmpty() ? new ArrayList<>(tableBodies.keySet()) : extractTableNames(schemaSql);

        if (tables.isEmpty()) {
            log.warn("未识别到任何 CREATE TABLE 语句，依赖图为空");
            return graph;
        }

        log.info("识别到 {} 张表: {}", tables.size(), tables);
        
        // 2) 先创建所有节点，便于后续建立跨表依赖
        for (String table : tables) {
            String entityName = toPascalCase(table);
            
            // 1. Entity Task (基础)
            String entityTaskId = "task_entity_" + table;
            graph.addNode(G3TaskDependencyGraph.G3TaskNode.builder()
                .id(entityTaskId)
                .type("entity")
                .name("Generate " + entityName + "Entity")
                .description("生成实体类 " + entityName + "Entity.java")
                .relatedEntity(entityName)
                .priority(10)
                .build());

            // 2. Mapper Task (依赖 Entity)
            String mapperTaskId = "task_mapper_" + table;
            graph.addNode(G3TaskDependencyGraph.G3TaskNode.builder()
                .id(mapperTaskId)
                .type("mapper")
                .name("Generate " + entityName + "Mapper")
                .description("生成Mapper接口 " + entityName + "Mapper.java")
                .relatedEntity(entityName)
                .priority(20)
                .build());
            graph.addDependency(entityTaskId, mapperTaskId);

            // 3. Service Task (依赖 Mapper + Entity)
            String serviceTaskId = "task_service_" + table;
            graph.addNode(G3TaskDependencyGraph.G3TaskNode.builder()
                .id(serviceTaskId)
                .type("service")
                .name("Generate " + entityName + "Service")
                .description("生成业务服务 " + entityName + "Service.java")
                .relatedEntity(entityName)
                .priority(30)
                .build());
            graph.addDependency(mapperTaskId, serviceTaskId);
            // Implicit dependency on Entity via Mapper, but strictly Service needs Mapper to validly compile mostly.
            // Adding direct dependency on Entity is also fine.
            graph.addDependency(entityTaskId, serviceTaskId);

            // 4. Controller Task (依赖 Service)
            String controllerTaskId = "task_controller_" + table;
            graph.addNode(G3TaskDependencyGraph.G3TaskNode.builder()
                .id(controllerTaskId)
                .type("controller")
                .name("Generate " + entityName + "Controller")
                .description("生成控制器 " + entityName + "Controller.java")
                .relatedEntity(entityName)
                .priority(40)
                .build());
            graph.addDependency(serviceTaskId, controllerTaskId);
        }

        // 3) 跨表依赖：根据外键引用，确保被引用实体优先生成（避免后续按表拆分时类引用缺失）
        if (!tableBodies.isEmpty()) {
            addForeignKeyEntityDependencies(graph, tableBodies);
        }
        
        return graph;
    }

    /**
     * 拓扑排序：获取可执行的任务列表
     * 
     * @param graph 依赖图
     * @return 排序后的任务节点列表
     */
    public List<G3TaskDependencyGraph.G3TaskNode> topologicalSort(G3TaskDependencyGraph graph) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return List.of();
        }

        // 建立节点索引
        Map<String, G3TaskDependencyGraph.G3TaskNode> nodeById = new HashMap<>();
        for (G3TaskDependencyGraph.G3TaskNode node : graph.getNodes()) {
            if (node == null || node.getId() == null || node.getId().isBlank()) continue;
            // 若重复 ID，保留第一份并记录告警（避免后续 NPE/乱序）
            if (nodeById.containsKey(node.getId())) {
                log.warn("[G3Dependency] 检测到重复任务ID，将忽略重复节点: id={}", node.getId());
                continue;
            }
            nodeById.put(node.getId(), node);
        }

        // 初始化入度与出边
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (String id : nodeById.keySet()) {
            indegree.put(id, 0);
            outgoing.put(id, new ArrayList<>());
        }

        if (graph.getEdges() != null) {
            for (G3TaskDependencyGraph.G3TaskEdge edge : graph.getEdges()) {
                if (edge == null) continue;
                String source = edge.getSource();
                String target = edge.getTarget();
                if (source == null || target == null) continue;
                if (!nodeById.containsKey(source) || !nodeById.containsKey(target)) {
                    log.debug("[G3Dependency] 忽略无效依赖边: {} -> {}", source, target);
                    continue;
                }
                outgoing.get(source).add(target);
                indegree.put(target, indegree.get(target) + 1);
            }
        }

        PriorityQueue<G3TaskDependencyGraph.G3TaskNode> ready = new PriorityQueue<>(DEFAULT_NODE_COMPARATOR);
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(nodeById.get(entry.getKey()));
            }
        }

        List<G3TaskDependencyGraph.G3TaskNode> sorted = new ArrayList<>(nodeById.size());
        Set<String> visited = new HashSet<>();

        while (!ready.isEmpty()) {
            G3TaskDependencyGraph.G3TaskNode node = ready.poll();
            if (node == null || node.getId() == null) continue;
            if (visited.contains(node.getId())) continue;
            visited.add(node.getId());
            sorted.add(node);

            for (String nextId : outgoing.getOrDefault(node.getId(), List.of())) {
                int nextIn = indegree.getOrDefault(nextId, 0) - 1;
                indegree.put(nextId, nextIn);
                if (nextIn == 0) {
                    ready.add(nodeById.get(nextId));
                }
            }
        }

        // 发生环/入度无法归零：回退为“优先级稳定排序”，保证不阻断主流程
        if (sorted.size() != nodeById.size()) {
            log.warn("[G3Dependency] 依赖图疑似存在环或无效依赖，回退为优先级排序: sorted={}, total={}",
                    sorted.size(), nodeById.size());
            List<G3TaskDependencyGraph.G3TaskNode> fallback = new ArrayList<>(nodeById.values());
            fallback.sort(DEFAULT_NODE_COMPARATOR);
            return fallback;
        }

        return sorted;
    }

    private Map<String, String> extractCreateTableBodies(String schemaSql) {
        Map<String, String> bodies = new HashMap<>();
        Matcher matcher = CREATE_TABLE_BLOCK_PATTERN.matcher(schemaSql);
        while (matcher.find()) {
            String name = matcher.group("name");
            String body = matcher.group("body");
            if (name == null || name.isBlank()) continue;
            bodies.put(name, body != null ? body : "");
        }
        return bodies;
    }

    private List<String> extractTableNames(String schemaSql) {
        Set<String> tables = new HashSet<>();
        Matcher matcher = CREATE_TABLE_NAME_PATTERN.matcher(schemaSql);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (name != null && !name.isBlank()) {
                tables.add(name);
            }
        }
        List<String> list = new ArrayList<>(tables);
        list.sort(String::compareTo);
        return list;
    }

    private void addForeignKeyEntityDependencies(G3TaskDependencyGraph graph, Map<String, String> tableBodies) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) return;
        if (tableBodies == null || tableBodies.isEmpty()) return;

        // 建表顺序优先，便于排查（但不强制）
        Set<String> knownTables = tableBodies.keySet();

        for (Map.Entry<String, String> entry : tableBodies.entrySet()) {
            String table = entry.getKey();
            String body = entry.getValue() != null ? entry.getValue() : "";

            String currentEntityTaskId = "task_entity_" + table;

            Matcher matcher = REFERENCES_PATTERN.matcher(body);
            while (matcher.find()) {
                String raw = matcher.group(1);
                String referenced = normalizeReferencedTable(raw);
                if (referenced == null || referenced.isBlank()) continue;
                if (!knownTables.contains(referenced)) continue;
                if (referenced.equalsIgnoreCase(table)) continue;

                String referencedEntityTaskId = "task_entity_" + referenced;
                graph.addDependency(referencedEntityTaskId, currentEntityTaskId);
            }
        }
    }

    private String normalizeReferencedTable(String rawToken) {
        if (rawToken == null) return null;
        String token = rawToken.trim();
        if (token.isEmpty()) return null;

        // 去掉可能的 schema 前缀：public.users / "public"."users"
        int dot = token.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < token.length()) {
            token = token.substring(dot + 1);
        }

        // 去掉引号/反引号
        token = token.replace("\"", "").replace("`", "");
        // 去掉尾部可能的逗号
        if (token.endsWith(",")) token = token.substring(0, token.length() - 1);
        return token.trim();
    }

    private String toPascalCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(Character.toLowerCase(c));
                }
            }
        }
        return sb.toString();
    }
}
