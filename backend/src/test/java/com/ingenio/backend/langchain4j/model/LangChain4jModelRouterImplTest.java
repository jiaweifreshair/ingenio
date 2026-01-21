package com.ingenio.backend.langchain4j.model;

import com.ingenio.backend.langchain4j.config.LangChain4jProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LangChain4jModelRouterImpl 测试。
 *
 * 是什么：模型路由器的单元测试。
 * 做什么：验证路由选择与降级策略。
 * 为什么：确保路由逻辑可控且可预期。
 */
public class LangChain4jModelRouterImplTest {

    /**
     * 验证按尝试次数降级选择模型。
     *
     * 是什么：路由降级规则测试。
     * 做什么：验证 attempt 递增时模型切换顺序。
     * 为什么：保证失败重试时的模型选择正确。
     */
    @Test
    void shouldSelectModelByAttempt() {
        LangChain4jProperties properties = buildProperties();
        LangChain4jModelRouter router = new LangChain4jModelRouterImpl(properties);

        LangChain4jModelRouter.ModelSelection first = router.select(
                LangChain4jModelRouter.TaskType.CODEGEN, 0, null);
        LangChain4jModelRouter.ModelSelection second = router.select(
                LangChain4jModelRouter.TaskType.CODEGEN, 1, null);
        LangChain4jModelRouter.ModelSelection third = router.select(
                LangChain4jModelRouter.TaskType.CODEGEN, 2, null);
        LangChain4jModelRouter.ModelSelection overflow = router.select(
                LangChain4jModelRouter.TaskType.CODEGEN, 9, null);

        assertEquals("claude", first.provider());
        assertEquals("claude-4-5", first.model());
        assertEquals("gemini", second.provider());
        assertEquals("gemini-3-pro", second.model());
        assertEquals("deepseek", third.provider());
        assertEquals("deepseek-v3", third.model());
        assertEquals("deepseek", overflow.provider());
    }

    /**
     * 构建路由测试配置。
     *
     * 是什么：测试配置构造方法。
     * 做什么：提供 router 所需的 routing/providers 配置。
     * 为什么：保证测试数据可控且可复用。
     *
     * @return 配置对象
     */
    private LangChain4jProperties buildProperties() {
        LangChain4jProperties properties = new LangChain4jProperties();
        LangChain4jProperties.Routing routing = new LangChain4jProperties.Routing();
        routing.setCodegen(List.of("claude", "gemini", "deepseek"));
        properties.setRouting(routing);

        LangChain4jProperties.Provider claude = new LangChain4jProperties.Provider();
        claude.setModel("claude-4-5");
        LangChain4jProperties.Provider gemini = new LangChain4jProperties.Provider();
        gemini.setModel("gemini-3-pro");
        LangChain4jProperties.Provider deepseek = new LangChain4jProperties.Provider();
        deepseek.setModel("deepseek-v3");

        HashMap<String, LangChain4jProperties.Provider> providers = new HashMap<>();
        providers.put("claude", claude);
        providers.put("gemini", gemini);
        providers.put("deepseek", deepseek);
        properties.setProviders(providers);

        return properties;
    }
}
