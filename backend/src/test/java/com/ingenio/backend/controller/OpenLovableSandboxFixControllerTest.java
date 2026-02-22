package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OpenLovableSandboxFixController 单元测试
 *
 * 目标：
 * - 验证“预览白屏兜底修复”在入口/根节点缺失时能正确写回必要文件；
 * - 避免依赖真实 open-lovable-cn/Docker 环境，使用 MockRestServiceServer 模拟上游接口。
 */
class OpenLovableSandboxFixControllerTest {

  @Test
  void smartFix_shouldCreateFallbackFilesWhenMissingOrBroken() {
    RestTemplate restTemplate = new RestTemplate();
    OpenLovableSandboxFixController controller = new OpenLovableSandboxFixController(restTemplate);
    ReflectionTestUtils.setField(controller, "openLovableBaseUrl", "http://open-lovable");

    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    // 1) index.html：存在入口脚本，但缺少 #root 容器（典型白屏原因之一）
    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat index.html")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"<!doctype html><html><body><script type=\\"module\\" src=\\"/src/main.jsx\\"></script></body></html>","stderr":""}
            """, MediaType.APPLICATION_JSON));

    // 2) src/main.jsx：内容存在但不包含挂载逻辑
    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/main.jsx")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"console.log('broken entry')","stderr":""}
            """, MediaType.APPLICATION_JSON));

    // 3) App 文件不存在：触发兜底 App.jsx 创建（包含一次额外的存在性检查）
    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/App.tsx")))
        .andRespond(withSuccess("""
            {"exitCode":1,"stdout":"cat: src/App.tsx: No such file or directory","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/App.jsx")))
        .andRespond(withSuccess("""
            {"exitCode":1,"stdout":"cat: src/App.jsx: No such file or directory","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/App.ts")))
        .andRespond(withSuccess("""
            {"exitCode":1,"stdout":"cat: src/App.ts: No such file or directory","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/App.js")))
        .andRespond(withSuccess("""
            {"exitCode":1,"stdout":"cat: src/App.js: No such file or directory","stderr":""}
            """, MediaType.APPLICATION_JSON));

    // 兜底逻辑会再次读取 src/App.jsx 判断是否已存在
    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/App.jsx")))
        .andRespond(withSuccess("""
            {"exitCode":1,"stdout":"cat: src/App.jsx: No such file or directory","stderr":""}
            """, MediaType.APPLICATION_JSON));

    // 写入兜底 App.jsx
    server.expect(requestTo("http://open-lovable/api/sandbox/write-files"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("src/App.jsx")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    // 写回标准 index.html（补 #root）
    server.expect(requestTo("http://open-lovable/api/sandbox/write-files"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("index.html")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    // 写回标准入口文件（补挂载）
    server.expect(requestTo("http://open-lovable/api/sandbox/write-files"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("src/main.jsx")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    // 4) src/index.css 不存在：创建空文件避免 import 失败
    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/index.css")))
        .andRespond(withSuccess("""
            {"exitCode":1,"stdout":"cat: src/index.css: No such file or directory","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/write-files"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("src/index.css")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    // 5) 依赖检查：node_modules/.vite/deps 不存在，触发安装
    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("ls node_modules/.vite/deps")))
        .andRespond(withSuccess("""
            {"exitCode":1,"stdout":"ls: cannot access 'node_modules/.vite/deps': No such file or directory","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("pnpm install")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"installed","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat node_modules/.vite/deps/_metadata.json")))
        .andRespond(withSuccess("""
            {"exitCode":1,"stdout":"No such file or directory","stderr":""}
            """, MediaType.APPLICATION_JSON));

    // 6) 依赖安装后重启 Vite
    server.expect(requestTo("http://open-lovable/api/restart-vite"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("sb_123")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    var response = controller.smartFix(Map.of("sandboxId", "sb_123"));
    server.verify();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isInstanceOf(Result.class);

    Result<?> body = (Result<?>) response.getBody();
    assertThat(body.getSuccess()).isTrue();
    assertThat(body.getData()).isInstanceOf(Map.class);

    Map<?, ?> data = (Map<?, ?>) body.getData();
    assertThat(data.get("fixed")).isEqualTo(true);

    @SuppressWarnings("unchecked")
    List<String> filesCreated = (List<String>) data.get("filesCreated");
    @SuppressWarnings("unchecked")
    List<String> filesUpdated = (List<String>) data.get("filesUpdated");
    @SuppressWarnings("unchecked")
    Map<String, Object> diagnostics = (Map<String, Object>) data.get("diagnostics");

    assertThat(filesCreated).contains("src/App.jsx", "src/index.css");
    assertThat(filesUpdated).contains("index.html", "src/main.jsx");
    assertThat(diagnostics.get("dependencyInstallAttempted")).isEqualTo(true);
    assertThat(diagnostics.get("dependencyInstallCommand")).isEqualTo("pnpm install");
    assertThat(diagnostics.get("viteRestarted")).isEqualTo(true);
  }

  /**
   * 是什么：依赖优化缓存重置测试。
   * 做什么：验证关键依赖缺失优化记录时会清理 .vite 并重启。
   * 为什么：避免 Vite 优化缺失导致预览资源 404。
   */
  @Test
  void smartFix_shouldResetOptimizeDepsWhenCriticalDepsMissing() {
    RestTemplate restTemplate = new RestTemplate();
    OpenLovableSandboxFixController controller = new OpenLovableSandboxFixController(restTemplate);
    ReflectionTestUtils.setField(controller, "openLovableBaseUrl", "http://open-lovable");

    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat index.html")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"<!doctype html><html><body><div id=\\"root\\"></div><script type=\\"module\\" src=\\"/src/main.jsx\\"></script></body></html>","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/main.jsx")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"import ReactDOM from 'react-dom/client'\\nconst root=document.getElementById('root')\\nReactDOM.createRoot(root).render(null)","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/App.tsx")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"export default function App(){return null}","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat src/index.css")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"body{}","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("ls node_modules/.vite/deps")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"node_modules/.vite/deps","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat node_modules/.vite/deps/_metadata.json")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"{\\"optimized\\":{}}","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("cat package.json")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"{\\"dependencies\\":{\\"lucide-react\\":\\"^0.1.0\\",\\"recharts\\":\\"^2.0.0\\"}}","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/sandbox/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("rm -rf node_modules/.vite")))
        .andRespond(withSuccess("""
            {"exitCode":0,"stdout":"removed","stderr":""}
            """, MediaType.APPLICATION_JSON));

    server.expect(requestTo("http://open-lovable/api/restart-vite"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("sb_456")))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    var response = controller.smartFix(Map.of("sandboxId", "sb_456"));
    server.verify();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isInstanceOf(Result.class);

    Result<?> body = (Result<?>) response.getBody();
    assertThat(body.getSuccess()).isTrue();
    assertThat(body.getData()).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.getData();
    @SuppressWarnings("unchecked")
    Map<String, Object> diagnostics = (Map<String, Object>) data.get("diagnostics");

    assertThat(data.get("fixed")).isEqualTo(true);
    assertThat(diagnostics.get("optimizeDepsReset")).isEqualTo(true);
    assertThat(diagnostics.get("viteRestarted")).isEqualTo(true);
    assertThat(diagnostics.get("optimizeDepsMissing").toString()).contains("lucide-react");
  }
}
