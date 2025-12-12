<#--
  ServiceClass.ftl - AI代码生成：完整Java Service类模板

  用途：将业务逻辑代码片段组装成完整的Java Service类
  评分目标：ValidationTool评分≥80/100

  输入参数：
  - packageName: 包名 (如 "com.ingenio.backend.service")
  - entityPackage: 实体包名 (如 "com.ingenio.backend.entity")
  - repositoryPackage: Repository包名 (如 "com.ingenio.backend.repository")
  - entityName: 实体类名 (如 "User")
  - entityVarName: 实体变量名 (如 "user")
  - serviceName: Service类名 (如 "UserService")
  - methodName: 方法名 (如 "createUser")
  - methodDescription: 方法描述 (如 "创建用户")
  - entityDescription: 实体描述 (如 "用户")
  - businessLogic: 业务逻辑代码片段
  - generatedDate: 生成日期
  - hasValidation: 是否包含验证逻辑
  - hasCalculation: 是否包含计算逻辑
  - hasWorkflow: 是否包含工作流逻辑
  - hasNotification: 是否包含通知逻辑

  @author Ingenio Code Generator
  @since 2025-11-19 V2.0 MVP Day 2 Phase 2.3.6
-->
package ${packageName};

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ${entityPackage}.${entityName};
import ${repositoryPackage}.${entityName}Repository;
<#if hasValidation!false>
import com.ingenio.backend.exception.BusinessException;
import com.ingenio.backend.exception.ErrorCode;
</#if>
<#if hasNotification!false>
import com.ingenio.backend.service.NotificationService;
</#if>
<#if hasWorkflow!false>
import com.ingenio.backend.service.WorkflowService;
</#if>

import java.time.LocalDateTime;
<#if hasCalculation!false>
import java.math.BigDecimal;
import java.math.RoundingMode;
</#if>
import java.util.Optional;

/**
 * ${serviceName} - ${entityDescription!"业务"}服务
 *
 * <p>提供${entityDescription!"实体"}相关的业务操作，包括：</p>
 * <ul>
 *   <li>${methodDescription!"业务处理"}</li>
<#if hasValidation!false>
 *   <li>数据验证和业务规则检查</li>
</#if>
<#if hasCalculation!false>
 *   <li>计算逻辑处理</li>
</#if>
<#if hasWorkflow!false>
 *   <li>工作流状态管理</li>
</#if>
<#if hasNotification!false>
 *   <li>通知消息发送</li>
</#if>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since ${generatedDate!"2025-11-19"}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ${serviceName} {

    /**
     * ${entityDescription!"实体"}数据访问层
     */
    private final ${entityName}Repository ${entityVarName}Repository;
<#if hasNotification!false>

    /**
     * 通知服务
     */
    private final NotificationService notificationService;
</#if>
<#if hasWorkflow!false>

    /**
     * 工作流服务
     */
    private final WorkflowService workflowService;
</#if>

    /**
     * ${methodDescription!"业务处理"}
     *
     * <p>执行${entityDescription!"实体"}的${methodDescription!"业务处理"}操作，包含以下步骤：</p>
     * <ol>
     *   <li>参数校验和前置检查</li>
     *   <li>执行业务逻辑</li>
     *   <li>持久化数据</li>
     *   <li>返回处理结果</li>
     * </ol>
     *
     * @param ${entityVarName} ${entityDescription!"实体"}对象
     * @return 处理后的${entityDescription!"实体"}
<#if hasValidation!false>
     * @throws BusinessException 当业务规则校验失败时抛出
</#if>
     */
    @Transactional
    public ${entityName} ${methodName}(${entityName} ${entityVarName}) {
        log.info("[${serviceName}] 开始执行${methodDescription!'业务处理'}: entityId={}",
                ${entityVarName}.getId() != null ? ${entityVarName}.getId() : "NEW");

        try {
            // 前置校验
            validateInput(${entityVarName});

            // ========== 业务逻辑开始 ==========
            ${businessLogic}
            // ========== 业务逻辑结束 ==========

            // 设置更新时间
            if (${entityVarName}.getUpdatedAt() != null) {
                ${entityVarName}.setUpdatedAt(LocalDateTime.now());
            }

            // 持久化到数据库
            ${entityName} saved = ${entityVarName}Repository.save(${entityVarName});

            log.info("[${serviceName}] ${methodDescription!'业务处理'}成功: id={}", saved.getId());
<#if hasNotification!false>

            // 发送通知
            sendNotification(saved);
</#if>

            return saved;

        } catch (BusinessException e) {
            log.warn("[${serviceName}] ${methodDescription!'业务处理'}业务异常: code={}, message={}",
                    e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[${serviceName}] ${methodDescription!'业务处理'}系统异常: {}", e.getMessage(), e);
            throw new RuntimeException("${methodDescription!'业务处理'}失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据ID查询${entityDescription!"实体"}
     *
     * @param id ${entityDescription!"实体"}ID
     * @return ${entityDescription!"实体"}（可能为空）
     */
    public Optional<${entityName}> findById(Long id) {
        log.debug("[${serviceName}] 查询${entityDescription!'实体'}: id={}", id);
        return ${entityVarName}Repository.findById(id);
    }

    /**
     * 根据ID获取${entityDescription!"实体"}（不存在则抛出异常）
     *
     * @param id ${entityDescription!"实体"}ID
     * @return ${entityDescription!"实体"}
     * @throws BusinessException 当${entityDescription!"实体"}不存在时抛出
     */
    public ${entityName} getById(Long id) {
        return findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "${entityDescription!'实体'}不存在: id=" + id));
    }

    /**
     * 删除${entityDescription!"实体"}
     *
     * @param id ${entityDescription!"实体"}ID
     */
    @Transactional
    public void deleteById(Long id) {
        log.info("[${serviceName}] 删除${entityDescription!'实体'}: id={}", id);

        if (!${entityVarName}Repository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "${entityDescription!'实体'}不存在: id=" + id);
        }

        ${entityVarName}Repository.deleteById(id);
        log.info("[${serviceName}] ${entityDescription!'实体'}删除成功: id={}", id);
    }

    /**
     * 输入参数校验
     *
     * @param ${entityVarName} 待校验的${entityDescription!"实体"}
     * @throws BusinessException 当校验失败时抛出
     */
    private void validateInput(${entityName} ${entityVarName}) {
        if (${entityVarName} == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "${entityDescription!'实体'}不能为空");
        }

        log.debug("[${serviceName}] 输入参数校验通过");
    }
<#if hasNotification!false>

    /**
     * 发送通知
     *
     * @param ${entityVarName} ${entityDescription!"实体"}
     */
    private void sendNotification(${entityName} ${entityVarName}) {
        try {
            notificationService.send("${methodDescription!'业务处理'}完成", ${entityVarName}.toString());
            log.debug("[${serviceName}] 通知发送成功");
        } catch (Exception e) {
            log.warn("[${serviceName}] 通知发送失败: {}", e.getMessage());
            // 通知失败不影响主流程
        }
    }
</#if>
}
