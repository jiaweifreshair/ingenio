package ${packageName};

import lombok.Data;
import lombok.Builder;

/**
 * ${className} 实体类
 *
 * <p>自动生成的测试类</p>
 *
 * @author ${author}
 * @since ${date}
 */
@Data
@Builder
public class ${className} {

<#list fields as field>
    /**
     * ${field.description}
     */
    private ${field.type} ${field.name};

</#list>
}
