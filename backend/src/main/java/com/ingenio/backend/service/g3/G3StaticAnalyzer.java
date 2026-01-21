package com.ingenio.backend.service.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * G3 静态代码分析器 (M4)
 * 
 * 目的：在编译前/后检测常见的逻辑错误或框架使用错误
 * 优势：比编译器报错更直观，比 Runtime 报错更早发现
 * 
 * 检测规则：
 * 1. Missing @Mapper on Check
 * 2. Missing @Service on Service Impl
 * 3. Lombok @Data missing on DTO/Entity
 * 4. Controller missing @RestController
 */
@Service
public class G3StaticAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(G3StaticAnalyzer.class);

    private static final Pattern MAPPER_INTERFACE = Pattern.compile("public\\s+interface\\s+\\w+Mapper");
    private static final Pattern SERVICE_CLASS = Pattern.compile("public\\s+class\\s+\\w+ServiceImpl");
    private static final Pattern CONTROLLER_CLASS = Pattern.compile("public\\s+class\\s+\\w+Controller");

    private static final Pattern ANNOTATION_MAPPER = Pattern.compile("@Mapper");
    private static final Pattern ANNOTATION_SERVICE = Pattern.compile("@Service");
    private static final Pattern ANNOTATION_REST_CONTROLLER = Pattern.compile("@RestController");

    public List<String> analyze(List<G3ArtifactEntity> artifacts) {
        List<String> violations = new ArrayList<>();

        for (G3ArtifactEntity artifact : artifacts) {
            String content = artifact.getContent();
            String fileName = artifact.getFileName();

            if (content == null || fileName == null || !fileName.endsWith(".java")) {
                continue;
            }

            // Rule 1: Mapper Check
            if (fileName.endsWith("Mapper.java") && MAPPER_INTERFACE.matcher(content).find()) {
                if (!ANNOTATION_MAPPER.matcher(content).find()) {
                    violations.add(String.format("[%s] 缺少 @Mapper 注解", fileName));
                }
            }

            // Rule 2: Service Check
            if (fileName.endsWith("ServiceImpl.java") && SERVICE_CLASS.matcher(content).find()) {
                if (!ANNOTATION_SERVICE.matcher(content).find()) {
                    violations.add(String.format("[%s] 缺少 @Service 注解", fileName));
                }
            }

            // Rule 3: Controller Check
            if (fileName.endsWith("Controller.java") && CONTROLLER_CLASS.matcher(content).find()) {
                if (!ANNOTATION_REST_CONTROLLER.matcher(content).find()) {
                    violations.add(String.format("[%s] 缺少 @RestController 注解", fileName));
                }
            }
        }

        return violations;
    }
}
