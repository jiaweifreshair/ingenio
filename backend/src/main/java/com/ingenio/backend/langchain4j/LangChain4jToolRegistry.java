package com.ingenio.backend.langchain4j;

import java.util.List;

/**
 * LangChain4j 工具注册表。
 *
 * 是什么：工具集合的统一容器。
 * 做什么：对外提供 Agent 可用的工具列表。
 * 为什么：避免工具注册分散导致遗漏或不可发现。
 */
public class LangChain4jToolRegistry {

    /**
     * 工具列表。
     *
     * 是什么：已注册的工具实例集合。
     * 做什么：供 Agent 进行工具调用。
     * 为什么：集中管理工具可提升一致性。
     */
    private final List<Object> tools;

    /**
     * 构造函数。
     *
     * 是什么：工具注册表初始化入口。
     * 做什么：注入工具实例列表。
     * 为什么：保证工具集合可被统一管理。
     *
     * @param tools 工具列表
     */
    public LangChain4jToolRegistry(List<Object> tools) {
        this.tools = tools;
    }

    /**
     * 获取工具列表。
     *
     * 是什么：工具集合查询方法。
     * 做什么：返回不可变的工具列表引用。
     * 为什么：保证 Agent 获取工具入口统一。
     *
     * @return 工具列表
     */
    public List<Object> tools() {
        return tools;
    }
}
