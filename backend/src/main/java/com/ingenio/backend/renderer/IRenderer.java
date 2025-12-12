package com.ingenio.backend.renderer;

import java.util.Map;

/**
 * 渲染器接口
 * 定义代码生成器的通用接口，支持扩展不同的UI框架渲染器
 */
public interface IRenderer {

    /**
     * 渲染AppSpec为代码
     *
     * @param appSpec AppSpec JSON（Map格式）
     * @return 生成的代码内容（文件名 -> 文件内容）
     */
    Map<String, String> render(Map<String, Object> appSpec);

    /**
     * 获取渲染器名称
     *
     * @return 渲染器名称
     */
    String getRendererName();

    /**
     * 获取渲染器支持的框架
     *
     * @return 框架名称（如：Taro、React、Vue等）
     */
    String getSupportedFramework();

    /**
     * 验证AppSpec是否适合该渲染器
     *
     * @param appSpec AppSpec JSON
     * @return 是否适合
     */
    boolean isSupported(Map<String, Object> appSpec);
}
