package com.ingenio.backend.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * MyBatis-Plus配置类
 * 配置分页插件、TypeHandler注册、字段自动填充等功能
 */
@Configuration
@MapperScan("com.ingenio.backend.mapper")
public class MyBatisPlusConfig {

    /**
     * 配置MyBatis-Plus拦截器（分页插件）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        // 设置最大单页限制数量,默认500条,-1不受限制
        paginationInterceptor.setMaxLimit(1000L);
        // 溢出总页数后是否进行处理（默认不处理）
        paginationInterceptor.setOverflow(false);

        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }

    /**
     * 配置自定义TypeHandler
     * 使用ConfigurationCustomizer而非覆盖整个sqlSessionFactory
     * 确保Spring Boot auto-configuration的所有TypeHandler（如JacksonTypeHandler）都能正确注册
     */
    @Bean
    public com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer configurationCustomizer() {
        return (MybatisConfiguration configuration) -> {
            // 注册UUIDv8TypeHandler处理UUID类型
            // 使用多种方式注册以确保autoResultMap=true时能正确找到TypeHandler
            UUIDv8TypeHandler uuidHandler = new UUIDv8TypeHandler();

            // 1. 默认注册（不指定JdbcType）- 用于autoResultMap和INSERT操作
            configuration.getTypeHandlerRegistry().register(UUID.class, uuidHandler);

            // 2. 同时注册JdbcType.OTHER、JdbcType.VARCHAR和null以覆盖所有PostgreSQL UUID场景
            configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, uuidHandler);
            configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.VARCHAR, uuidHandler);
            // 3. 注册null JdbcType以处理INSERT时未指定JdbcType的情况
            configuration.getTypeHandlerRegistry().register(UUID.class, null, uuidHandler);

            // 显式注册JacksonTypeHandler处理JSONB类型
            // 确保@TableField(typeHandler = JacksonTypeHandler.class)的Entity能正确序列化/反序列化
            com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler jacksonTypeHandler =
                new com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler(Object.class);
            configuration.getTypeHandlerRegistry().register(jacksonTypeHandler);
        };
    }

}
