package com.ingenio.backend.codegen.template;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Freemarker模板引擎（V2.0 Phase 3核心组件）
 *
 * <p>负责加载和渲染Freemarker模板，用于Spring Boot代码生成</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>多源模板加载：支持从Classpath和文件系统加载模板</li>
 *   <li>模板渲染：将数据模型渲染为Java代码</li>
 *   <li>错误处理：完整的异常处理和日志记录</li>
 *   <li>性能优化：模板缓存和复用</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @Autowired
 * private TemplateEngine templateEngine;
 *
 * public String generateEntityCode(EntityTemplateModel model) {
 *     return templateEngine.render("Entity.ftl", model.toMap());
 * }
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.1: 模板引擎基础设施
 */
@Slf4j
@Component
public class TemplateEngine {

    /**
     * Freemarker配置对象
     * 在构造函数中初始化，配置模板加载器、编码、异常处理等
     */
    private final Configuration configuration;

    /**
     * 模板基础路径（Classpath相对路径）
     * 默认值：/templates/codegen/
     */
    private static final String DEFAULT_TEMPLATE_BASE_PATH = "/templates/codegen/";

    /**
     * 构造函数：初始化Freemarker配置
     *
     * <p>配置项：</p>
     * <ul>
     *   <li>版本：Freemarker 2.3.32</li>
     *   <li>编码：UTF-8</li>
     *   <li>模板加载器：Classpath + 文件系统（多源）</li>
     *   <li>异常处理：HTML转义关闭（生成Java代码不需要转义）</li>
     *   <li>缓存：强缓存（生产环境）或延迟更新（开发环境）</li>
     * </ul>
     */
    public TemplateEngine() {
        log.info("[TemplateEngine] 初始化Freemarker模板引擎");

        // 1. 创建Configuration对象（设置Freemarker版本）
        this.configuration = new Configuration(Configuration.VERSION_2_3_32);

        // 2. 配置字符编码
        this.configuration.setDefaultEncoding("UTF-8");

        // 3. 配置模板加载器（多源：Classpath + 文件系统）
        configureTemplateLoaders();

        // 4. 配置异常处理
        this.configuration.setLogTemplateExceptions(false); // 不记录模板异常到日志（我们自己处理）
        this.configuration.setWrapUncheckedExceptions(true); // 包装未检查异常

        // 5. 配置数字格式（Java代码生成不需要千分位）
        this.configuration.setNumberFormat("0.######");

        log.info("[TemplateEngine] ✅ Freemarker模板引擎初始化完成");
    }

    /**
     * 配置多源模板加载器
     *
     * <p>加载顺序：</p>
     * <ol>
     *   <li>优先从Classpath加载（打包后的模板）</li>
     *   <li>其次从文件系统加载（开发环境的自定义模板）</li>
     * </ol>
     */
    private void configureTemplateLoaders() {
        try {
            // 加载器1：从Classpath加载（src/main/resources/templates/codegen/）
            ClassTemplateLoader classTemplateLoader = new ClassTemplateLoader(
                    getClass(),
                    DEFAULT_TEMPLATE_BASE_PATH
            );

            // 加载器2：从文件系统加载（项目根目录/templates/codegen/）
            FileTemplateLoader fileTemplateLoader = new FileTemplateLoader(
                    new File("templates/codegen")
            );

            // 组合加载器（优先Classpath，其次文件系统）
            MultiTemplateLoader multiTemplateLoader = new MultiTemplateLoader(
                    new TemplateLoader[]{classTemplateLoader, fileTemplateLoader}
            );

            this.configuration.setTemplateLoader(multiTemplateLoader);

            log.info("[TemplateEngine] 模板加载器配置完成：Classpath + FileSystem");

        } catch (IOException e) {
            log.warn("[TemplateEngine] ⚠️ 文件系统模板加载器配置失败，仅使用Classpath加载器: {}",
                    e.getMessage());

            // 降级：仅使用Classpath加载器
            ClassTemplateLoader classTemplateLoader = new ClassTemplateLoader(
                    getClass(),
                    DEFAULT_TEMPLATE_BASE_PATH
            );
            this.configuration.setTemplateLoader(classTemplateLoader);
        }
    }

    /**
     * 渲染模板（核心方法）
     *
     * <p>将数据模型注入模板并生成最终代码</p>
     *
     * <p>流程：</p>
     * <ol>
     *   <li>根据模板名称加载模板</li>
     *   <li>创建StringWriter输出缓冲区</li>
     *   <li>执行模板渲染</li>
     *   <li>返回生成的代码字符串</li>
     * </ol>
     *
     * @param templateName 模板文件名（如：Entity.ftl）
     * @param dataModel 数据模型（Map格式）
     * @return 渲染后的代码字符串
     * @throws TemplateRenderException 当模板加载或渲染失败时抛出
     */
    public String render(String templateName, Map<String, Object> dataModel) {
        log.info("[TemplateEngine] 开始渲染模板: {}", templateName);

        try {
            // Step 1: 加载模板
            Template template = configuration.getTemplate(templateName);
            log.debug("[TemplateEngine] 模板加载成功: {}", templateName);

            // Step 2: 创建输出缓冲区
            StringWriter stringWriter = new StringWriter();

            // Step 3: 渲染模板
            template.process(dataModel, stringWriter);

            // Step 4: 获取渲染结果
            String renderedCode = stringWriter.toString();
            log.info("[TemplateEngine] ✅ 模板渲染完成: {} (长度: {} 字符)",
                    templateName, renderedCode.length());

            return renderedCode;

        } catch (IOException e) {
            log.error("[TemplateEngine] ❌ 模板加载失败: {}", templateName, e);
            throw new TemplateRenderException(
                    String.format("模板加载失败: %s", templateName), e
            );

        } catch (TemplateException e) {
            log.error("[TemplateEngine] ❌ 模板渲染失败: {}", templateName, e);
            throw new TemplateRenderException(
                    String.format("模板渲染失败: %s, 错误信息: %s",
                            templateName, e.getMessage()), e
            );
        }
    }

    /**
     * 检查模板是否存在
     *
     * @param templateName 模板文件名
     * @return true如果模板存在，false否则
     */
    public boolean templateExists(String templateName) {
        try {
            configuration.getTemplate(templateName);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 模板渲染异常
     *
     * <p>当模板加载或渲染过程中发生错误时抛出</p>
     */
    public static class TemplateRenderException extends RuntimeException {
        public TemplateRenderException(String message) {
            super(message);
        }

        public TemplateRenderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
