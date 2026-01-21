package com.ingenio.backend.codegen.template;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Freemarker模板引擎（V2.0 Phase 3核心组件）
 */
@Component
public class TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(TemplateEngine.class);

    private final Configuration configuration;
    private static final String DEFAULT_TEMPLATE_BASE_PATH = "/templates/codegen/";

    public TemplateEngine() {
        log.info("[TemplateEngine] 初始化Freemarker模板引擎");

        this.configuration = new Configuration(Configuration.VERSION_2_3_32);
        this.configuration.setDefaultEncoding("UTF-8");
        configureTemplateLoaders();
        this.configuration.setLogTemplateExceptions(false);
        this.configuration.setWrapUncheckedExceptions(true);
        this.configuration.setNumberFormat("0.######");

        log.info("[TemplateEngine] ✅ Freemarker模板引擎初始化完成");
    }

    private void configureTemplateLoaders() {
        try {
            ClassTemplateLoader classTemplateLoader = new ClassTemplateLoader(
                    getClass(),
                    DEFAULT_TEMPLATE_BASE_PATH);

            FileTemplateLoader fileTemplateLoader = new FileTemplateLoader(
                    new File("templates/codegen"));

            MultiTemplateLoader multiTemplateLoader = new MultiTemplateLoader(
                    new TemplateLoader[] { classTemplateLoader, fileTemplateLoader });

            this.configuration.setTemplateLoader(multiTemplateLoader);
            log.info("[TemplateEngine] 模板加载器配置完成：Classpath + FileSystem");

        } catch (IOException e) {
            log.warn("[TemplateEngine] ⚠️ 文件系统模板加载器配置失败，仅使用Classpath加载器: {}",
                    e.getMessage());

            ClassTemplateLoader classTemplateLoader = new ClassTemplateLoader(
                    getClass(),
                    DEFAULT_TEMPLATE_BASE_PATH);
            this.configuration.setTemplateLoader(classTemplateLoader);
        }
    }

    public String render(String templateName, Map<String, Object> dataModel) {
        log.info("[TemplateEngine] 开始渲染模板: {}", templateName);

        try {
            Template template = configuration.getTemplate(templateName);
            log.debug("[TemplateEngine] 模板加载成功: {}", templateName);

            StringWriter stringWriter = new StringWriter();
            template.process(dataModel, stringWriter);

            String renderedCode = stringWriter.toString();
            log.info("[TemplateEngine] ✅ 模板渲染完成: {} (长度: {} 字符)",
                    templateName, renderedCode.length());

            return renderedCode;

        } catch (IOException e) {
            log.error("[TemplateEngine] ❌ 模板加载失败: {}", templateName, e);
            throw new TemplateRenderException(
                    String.format("模板加载失败: %s", templateName), e);

        } catch (TemplateException e) {
            log.error("[TemplateEngine] ❌ 模板渲染失败: {}", templateName, e);
            throw new TemplateRenderException(
                    String.format("模板渲染失败: %s, 错误信息: %s",
                            templateName, e.getMessage()),
                    e);
        }
    }

    public boolean templateExists(String templateName) {
        try {
            configuration.getTemplate(templateName);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static class TemplateRenderException extends RuntimeException {
        public TemplateRenderException(String message) {
            super(message);
        }

        public TemplateRenderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
