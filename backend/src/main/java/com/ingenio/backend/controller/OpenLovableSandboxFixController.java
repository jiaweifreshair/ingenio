package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Open-Lovable 沙箱智能修复控制器
 *
 * 是什么：
 * - 为“原型预览 iframe 白屏但无显式报错”的场景提供一个可手动触发的兜底修复入口。
 *
 * 做什么：
 * - 通过 Open-Lovable-CN 的 `/api/sandbox/execute`
 * 读取关键文件（index.html、src/main.*、src/App.*）。
 * - 若检测到入口文件未挂载（缺少 ReactDOM.createRoot(...).render(...)），则自动写回标准入口文件。
 * - 若 App 文件不存在，则创建最小可运行的兜底 App.jsx，保证预览不再空白。
 *
 * 为什么：
 * - iframe 白屏在很多情况下不会触发 onError，也不会在控制台直接暴露（跨域/被屏蔽）。
 * - 用户需要一个“可点击即恢复可见”的救生按钮，避免因入口脚本缺失挂载导致卡死。
 */
@Slf4j
@RestController
@RequestMapping("/v1/openlovable/sandbox")
@RequiredArgsConstructor
public class OpenLovableSandboxFixController {

  /**
   * Open-Lovable-CN 服务基础URL
   */
  @Value("${ingenio.openlovable.base-url:http://localhost:3001}")
  private String openLovableBaseUrl;

  private final RestTemplate restTemplate;

  /**
   * Open-Lovable 沙箱工作目录（E2B 模板默认）
   */
  private static final String SANDBOX_WORKDIR = "/home/user/app";

  /**
   * 从 index.html 推断入口脚本（如 /src/main.jsx）
   */
  private static final Pattern ENTRY_SCRIPT_PATTERN = Pattern
      .compile("src\\s*=\\s*['\"]/src/(main\\.(?:jsx|tsx|js|ts))['\"]");

  /**
   * 判断 index.html 是否包含 root 容器
   */
  private static final Pattern ROOT_CONTAINER_PATTERN = Pattern.compile("id\\s*=\\s*['\"]root['\"]");

  /**
   * 判断入口文件是否已包含挂载逻辑
   */
  private static final Pattern HAS_MOUNT_CALL_PATTERN = Pattern
      .compile("ReactDOM\\s*\\.\\s*(createRoot|render)\\s*\\(|\\bcreateRoot\\s*\\(");

  /**
   * 判断入口文件是否指向 root 容器
   */
  private static final Pattern TARGETS_ROOT_PATTERN = Pattern
      .compile("getElementById\\s*\\(\\s*['\"]root['\"]\\s*\\)|querySelector\\s*\\(\\s*['\"]#root['\"]\\s*\\)");

  /**
   * 智能修复沙箱（入口挂载/兜底 App）
   *
   * 请求体：
   * {
   * "sandboxId": "xxxx"
   * }
   */
  @PostMapping("/inject-error-handler")
  public ResponseEntity<?> injectErrorHandler(@RequestBody Map<String, Object> request) {
    Object sandboxIdObj = request.get("sandboxId");
    if (!(sandboxIdObj instanceof String sandboxId) || sandboxId.isBlank()) {
      return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: sandboxId"));
    }

    try {
      // 1. 读取当前的 index.html
      String indexHtml = readFile(sandboxId, "index.html", 10);

      // 2. 推断入口
      String entryPath = inferEntryPathFromIndex(indexHtml);
      if (entryPath == null)
        entryPath = "src/main.jsx";

      // 3. 使用 buildStandardIndexHtml 重建 (已包含 script)
      String fixedIndexHtml = buildStandardIndexHtml(entryPath);

      // 4. 写入
      writeFile(sandboxId, "index.html", fixedIndexHtml);

      return ResponseEntity.ok(Result.success(Map.of("injected", true)));
    } catch (Exception e) {
      log.error("[OpenLovableSandboxFix] 注入错误处理脚本失败", e);
      return ResponseEntity.internalServerError().body(Result.error("注入失败: " + e.getMessage()));
    }
  }

  /**
   * 智能修复沙箱（入口挂载/兜底 App）
   *
   * 请求体：
   * {
   * "sandboxId": "xxxx"
   * }
   */
  @PostMapping("/smart-fix")
  public ResponseEntity<?> smartFix(@RequestBody Map<String, Object> request) {
    Object sandboxIdObj = request.get("sandboxId");
    if (!(sandboxIdObj instanceof String sandboxId) || sandboxId.isBlank()) {
      return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: sandboxId"));
    }

    try {
      Map<String, Object> result = smartFixInternal(sandboxId.trim());
      return ResponseEntity.ok(Result.success(result));
    } catch (Exception e) {
      log.error("[OpenLovableSandboxFix] 智能修复失败: sandboxId={}", sandboxId, e);
      return ResponseEntity.internalServerError().body(Result.error("智能修复失败: " + e.getMessage()));
    }
  }

  /**
   * 智能修复核心逻辑
   *
   * 做什么：
   * - 读取 index.html、入口文件、App 文件；
   * - 判断是否缺少 root 容器/入口挂载；
   * - 必要时写回标准 index.html + 标准入口文件 + 兜底 App.jsx + 空 index.css（确保 import 不报错）。
   *
   * 为什么：
   * - 预览 iframe 白屏往往是“入口未挂载/根节点缺失/入口脚本丢失”等低级但高频问题；
   * - 该方法提供一个可重复执行、幂等的兜底修复策略。
   */
  private Map<String, Object> smartFixInternal(String sandboxId) {
    final List<String> filesCreated = new ArrayList<>();
    final List<String> filesUpdated = new ArrayList<>();
    final Map<String, Object> diagnostics = new HashMap<>();

    String indexHtml = readFile(sandboxId, "index.html", 15);
    String entryPath = inferEntryPathFromIndex(indexHtml);
    boolean indexExists = indexHtml != null;
    diagnostics.put("indexHtmlFound", indexHtml != null);

    if (entryPath == null) {
      entryPath = inferEntryPathByProbing(sandboxId);
    }
    if (entryPath == null) {
      // 兜底：与 Open-Lovable 模板默认保持一致
      entryPath = "src/main.jsx";
    }

    diagnostics.put("entryPath", entryPath);

    String entryContent = readFile(sandboxId, entryPath, 15);
    boolean entryExists = entryContent != null;
    if (entryContent == null)
      entryContent = "";

    boolean looksMounted = HAS_MOUNT_CALL_PATTERN.matcher(entryContent).find()
        && TARGETS_ROOT_PATTERN.matcher(entryContent).find();
    diagnostics.put("entryMounted", looksMounted);

    String appImport = inferAppImportPath(sandboxId);
    if (appImport == null) {
      // 没有 App 文件时，创建一个最小可运行的兜底 App.jsx，保证预览不再空白
      String fallbackAppPath = "src/App.jsx";
      String fallbackAppContent = buildFallbackApp();
      boolean existed = readFile(sandboxId, fallbackAppPath, 10) != null;
      writeFile(sandboxId, fallbackAppPath, fallbackAppContent);
      diagnostics.put("fallbackAppCreated", true);
      if (existed) {
        filesUpdated.add(fallbackAppPath);
      } else {
        filesCreated.add(fallbackAppPath);
      }
      appImport = "./App.jsx";
    }
    diagnostics.put("appImport", appImport);

    // index.html 缺失 root 或入口脚本不一致：重写为标准 index.html
    boolean indexNeedsFix = false;
    String existingIndexEntry = inferEntryPathFromIndex(indexHtml);
    boolean indexHasRoot = indexHtml != null && ROOT_CONTAINER_PATTERN.matcher(indexHtml).find();
    boolean indexEntryMatches = existingIndexEntry != null && existingIndexEntry.equals(entryPath);
    if (indexHtml == null || indexHtml.isBlank())
      indexNeedsFix = true;
    if (!indexHasRoot)
      indexNeedsFix = true;
    if (!indexEntryMatches)
      indexNeedsFix = true;

    diagnostics.put("indexHasRoot", indexHasRoot);
    diagnostics.put("indexEntryPath", existingIndexEntry);
    diagnostics.put("indexEntryMatches", indexEntryMatches);

    if (indexNeedsFix) {
      String fixedIndexHtml = buildStandardIndexHtml(entryPath);
      writeFile(sandboxId, "index.html", fixedIndexHtml);
      diagnostics.put("indexHtmlFixed", true);
      if (indexExists) {
        filesUpdated.add("index.html");
      } else {
        filesCreated.add("index.html");
      }
    } else {
      diagnostics.put("indexHtmlFixed", false);
    }

    // 入口文件缺少挂载逻辑：重写为标准入口文件
    if (!looksMounted) {
      String fixedEntry = buildStandardEntry(appImport);
      writeFile(sandboxId, entryPath, fixedEntry);
      if (entryExists) {
        filesUpdated.add(entryPath);
      } else {
        filesCreated.add(entryPath);
      }
    }

    // 入口文件会 import ./index.css；若缺失则创建空文件避免构建报错
    String indexCssPath = "src/index.css";
    boolean indexCssExists = readFile(sandboxId, indexCssPath, 10) != null;
    diagnostics.put("indexCssFound", indexCssExists);
    if (!indexCssExists) {
      writeFile(sandboxId, indexCssPath, "/* 自动兜底生成：避免入口 import 失败 */\n");
      filesCreated.add(indexCssPath);
      diagnostics.put("indexCssCreated", true);
    } else {
      diagnostics.put("indexCssCreated", false);
    }

    boolean fixed = !filesCreated.isEmpty() || !filesUpdated.isEmpty();

    return Map.of(
        "fixed", fixed,
        "filesCreated", filesCreated,
        "filesUpdated", filesUpdated,
        "diagnostics", diagnostics,
        "message", fixed ? "已应用兜底修复，建议刷新预览" : "未检测到可自动修复的问题",
        "timestamp", Instant.now().toString());
  }

  /**
   * 从 index.html 解析入口路径（优先读取 /src/main.*）
   */
  private String inferEntryPathFromIndex(String indexHtml) {
    if (indexHtml == null || indexHtml.isBlank())
      return null;
    Matcher matcher = ENTRY_SCRIPT_PATTERN.matcher(indexHtml);
    if (!matcher.find())
      return null;
    return "src/" + matcher.group(1);
  }

  /**
   * 当 index.html 无法解析入口时，通过探测常见入口文件名推断
   */
  private String inferEntryPathByProbing(String sandboxId) {
    String[] candidates = new String[] {
        "src/main.jsx",
        "src/main.tsx",
        "src/main.js",
        "src/main.ts"
    };

    for (String candidate : candidates) {
      String content = readFile(sandboxId, candidate, 10);
      if (content != null && !content.isBlank()) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * 推断 App 组件导入路径（优先使用已存在的 App.tsx/App.jsx/App.ts/App.js）
   */
  private String inferAppImportPath(String sandboxId) {
    String[] candidates = new String[] {
        "src/App.tsx",
        "src/App.jsx",
        "src/App.ts",
        "src/App.js"
    };

    for (String candidate : candidates) {
      String content = readFile(sandboxId, candidate, 10);
      if (content != null && !content.isBlank()) {
        // 注意：入口文件位于 src/ 下，import 应为相对 ./App.xxx
        String ext = candidate.substring(candidate.lastIndexOf('.'));
        return "./App" + ext;
      }
    }
    return null;
  }

  /**
   * 构建标准 React 入口文件内容（确保挂载到 #root）
   */
  private String buildStandardEntry(String appImport) {
    return "import React from 'react'\n"
        + "import ReactDOM from 'react-dom/client'\n"
        + "import App from '" + appImport + "'\n"
        + "import './index.css'\n"
        + "\n"
        + "const rootElement = document.getElementById('root')\n"
        + "if (!rootElement) throw new Error('Root element not found')\n"
        + "\n"
        + "ReactDOM.createRoot(rootElement).render(\n"
        + "  <React.StrictMode>\n"
        + "    <App />\n"
        + "  </React.StrictMode>\n"
        + ")\n";
  }

  /**
   * 构建标准 index.html（确保存在 #root 与正确入口脚本）
   */
  private String buildStandardIndexHtml(String entryPath) {
    String scriptSrc = "/" + entryPath.replaceFirst("^/+", "");
    return "<!doctype html>\n"
        + "<html lang=\"zh-CN\">\n"
        + "  <head>\n"
        + "    <meta charset=\"UTF-8\" />\n"
        + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n"
        + "    <title>Ingenio 预览</title>\n"
        + "    <script>\n"
        + "      window.onerror = function(message, source, lineno, colno, error) {\n"
        + "        window.parent.postMessage({ type: 'SANDBOX_ERROR', error: { message, source, lineno, colno, stack: error?.stack } }, '*');\n"
        + "      };\n"
        + "      window.onunhandledrejection = function(event) {\n"
        + "        window.parent.postMessage({ type: 'SANDBOX_ERROR', error: { message: event.reason?.message || event.reason, stack: event.reason?.stack } }, '*');\n"
        + "      };\n"
        + "    </script>\n"
        + "  </head>\n"
        + "  <body>\n"
        + "    <div id=\"root\"></div>\n"
        + "    <script type=\"module\" src=\"" + scriptSrc + "\"></script>\n"
        + "  </body>\n"
        + "</html>\n";
  }

  /**
   * 构建兜底 App.jsx（用于 App 缺失/为空时，避免预览白屏）
   */
  private String buildFallbackApp() {
    return "export default function App() {\n"
        + "  return (\n"
        + "    <div style={{ padding: 24, fontFamily: 'system-ui, -apple-system, Segoe UI, Roboto, sans-serif' }}>\n"
        + "      <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 8 }}>预览已恢复</h1>\n"
        + "      <p style={{ margin: 0, color: '#555' }}>\n"
        + "        检测到入口/应用文件异常，系统已生成兜底页面以避免白屏。\\n"
        + "        请返回生成页重新生成或继续修复缺失的组件与依赖。\n"
        + "      </p>\n"
        + "    </div>\n"
        + "  );\n"
        + "}\n";
  }

  /**
   * 读取沙箱内文件（仅用于诊断）
   *
   * @return 文件内容；若文件不存在或读取失败返回 null
   */
  private String readFile(String sandboxId, String relativePath, int timeoutSeconds) {
    SandboxExecResult exec = executeCommand(sandboxId, "cat " + relativePath, timeoutSeconds);
    if (exec.exitCode != 0)
      return null;

    String output = exec.stdout != null ? exec.stdout : "";
    if (output.contains("No such file") || output.contains("not found"))
      return null;
    return output;
  }

  /**
   * 写入沙箱内文件（兜底修复会用到）
   */
  private void writeFile(String sandboxId, String relativePath, String content) {
    String url = openLovableBaseUrl + "/api/sandbox/write-files";

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("sandboxId", sandboxId);

    List<Map<String, String>> files = new ArrayList<>();
    Map<String, String> file = new HashMap<>();
    file.put("path", SANDBOX_WORKDIR + "/" + relativePath);
    file.put("content", content);
    files.add(file);

    requestBody.put("files", files);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

    restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
  }

  /**
   * 在沙箱内执行命令（用于读取文件/基础诊断）
   */
  private SandboxExecResult executeCommand(String sandboxId, String command, int timeoutSeconds) {
    String url = openLovableBaseUrl + "/api/sandbox/execute";

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("sandboxId", sandboxId);
    requestBody.put("command", command);
    requestBody.put("timeout", timeoutSeconds);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
    Map<?, ?> body = response.getBody() != null ? response.getBody() : Map.of();

    int exitCode = toInt(body.get("exitCode"), 0);
    Object stdoutObj = body.get("stdout");
    if (stdoutObj == null)
      stdoutObj = body.get("output");
    if (stdoutObj == null)
      stdoutObj = body.get("message");

    Object stderrObj = body.get("stderr");

    return new SandboxExecResult(exitCode, toString(stdoutObj), toString(stderrObj));
  }

  /**
   * 将对象安全转换为 int
   */
  private int toInt(Object value, int fallback) {
    if (value instanceof Number n)
      return n.intValue();
    if (value instanceof String s) {
      try {
        return Integer.parseInt(s.trim());
      } catch (NumberFormatException e) {
        return fallback;
      }
    }
    return fallback;
  }

  /**
   * 将对象安全转换为 String
   */
  private String toString(Object value) {
    if (value == null)
      return "";
    return String.valueOf(value);
  }

  /**
   * 沙箱命令执行结果
   *
   * @param exitCode 命令退出码
   * @param stdout   标准输出
   * @param stderr   标准错误
   */
  private record SandboxExecResult(int exitCode, String stdout, String stderr) {
  }
}
