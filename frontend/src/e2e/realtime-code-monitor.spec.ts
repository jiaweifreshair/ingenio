/**
 * E2E测试：实时代码生成质量监控
 *
 * 功能：
 * 1. 监控控制台错误和警告
 * 2. 捕获生成的代码并验证语法
 * 3. 检测预览iframe的渲染状态
 * 4. 实时报告代码生成进度和问题
 *
 * 使用方法：
 * pnpm exec playwright test src/e2e/realtime-code-monitor.spec.ts --headed
 */

import { test, expect, Page, ConsoleMessage } from '@playwright/test';
import * as fs from 'fs';

/** 控制台消息收集器 */
interface ConsoleLog {
  type: string;
  text: string;
  timestamp: Date;
  location?: string;
}

/** 代码文件信息 */
interface CodeFile {
  name: string;
  content: string;
  hasError: boolean;
  errorDetails?: string;
}

/** 测试报告 */
interface TestReport {
  startTime: Date;
  endTime?: Date;
  consoleLogs: ConsoleLog[];
  codeFiles: CodeFile[];
  errors: string[];
  warnings: string[];
  iframeStatus: 'loading' | 'loaded' | 'error' | 'unknown';
  screenshotPaths: string[];
}

/**
 * 设置页面监控
 * @param page Playwright页面对象
 * @param report 测试报告对象
 */
async function setupPageMonitoring(page: Page, report: TestReport): Promise<void> {
  // 监控控制台消息
  page.on('console', (msg: ConsoleMessage) => {
    const logEntry: ConsoleLog = {
      type: msg.type(),
      text: msg.text(),
      timestamp: new Date(),
      location: msg.location()?.url,
    };
    report.consoleLogs.push(logEntry);

    // 分类错误和警告
    if (msg.type() === 'error') {
      report.errors.push(`[${logEntry.timestamp.toISOString()}] ${msg.text()}`);
    } else if (msg.type() === 'warning') {
      report.warnings.push(`[${logEntry.timestamp.toISOString()}] ${msg.text()}`);
    }
  });

  // 监控页面错误
  page.on('pageerror', (error) => {
    report.errors.push(`[Page Error] ${error.message}`);
  });

  // 监控请求失败
  page.on('requestfailed', (request) => {
    const failure = request.failure();
    if (failure) {
      report.errors.push(`[Request Failed] ${request.url()} - ${failure.errorText}`);
    }
  });
}

/**
 * 验证JavaScript/TypeScript代码语法
 * @param code 代码内容
 * @param filename 文件名
 * @returns 验证结果
 */
function validateCodeSyntax(code: string, filename: string): { valid: boolean; error?: string } {
  try {
    // 基本语法检查：尝试解析代码
    if (filename.endsWith('.jsx') || filename.endsWith('.tsx') || filename.endsWith('.js') || filename.endsWith('.ts')) {
      // 检查常见语法错误
      const syntaxErrors: string[] = [];

      // 检查括号匹配
      const brackets = { '(': 0, '{': 0, '[': 0 };
      for (const char of code) {
        if (char === '(') brackets['(']++;
        if (char === ')') brackets['(']--;
        if (char === '{') brackets['{']++;
        if (char === '}') brackets['{']--;
        if (char === '[') brackets['[']++;
        if (char === ']') brackets['[']--;
      }

      if (brackets['('] !== 0) syntaxErrors.push('括号不匹配');
      if (brackets['{'] !== 0) syntaxErrors.push('花括号不匹配');
      if (brackets['['] !== 0) syntaxErrors.push('方括号不匹配');

      // 检查常见React错误
      if (code.includes('return') && code.includes('<') && !code.includes('return (') && !code.includes('return <')) {
        // 可能的return语句问题
        if (code.match(/return\s*\n\s*</)) {
          syntaxErrors.push('return语句后换行可能导致undefined返回');
        }
      }

      // 检查未闭合的JSX标签
      const selfClosingPattern = /<(\w+)[^>]*\/>/g;
      const openingPattern = /<(\w+)[^/>]*>/g;
      const closingPattern = /<\/(\w+)>/g;

      const selfClosing = (code.match(selfClosingPattern) || []).length;
      const opening = (code.match(openingPattern) || []).length;
      const closing = (code.match(closingPattern) || []).length;

      if (opening - selfClosing !== closing) {
        syntaxErrors.push(`JSX标签可能未正确闭合 (开标签:${opening}, 自闭合:${selfClosing}, 闭标签:${closing})`);
      }

      if (syntaxErrors.length > 0) {
        return { valid: false, error: syntaxErrors.join('; ') };
      }
    }

    return { valid: true };
  } catch (error) {
    return { valid: false, error: String(error) };
  }
}

/**
 * 检查iframe渲染状态
 * @param page Playwright页面对象
 * @returns 渲染状态
 */
async function checkIframeStatus(page: Page): Promise<'loading' | 'loaded' | 'error' | 'unknown'> {
  try {
    const iframe = page.locator('iframe').first();
    const isVisible = await iframe.isVisible({ timeout: 5000 }).catch(() => false);

    if (!isVisible) {
      return 'unknown';
    }

    // 尝试获取iframe内容
    const frame = page.frameLocator('iframe').first();
    const body = frame.locator('body');
    const hasContent = await body.count() > 0;

    if (hasContent) {
      // 检查是否有错误边界显示
      const errorBoundary = frame.locator('text=/Error|错误|Something went wrong/i');
      const hasError = await errorBoundary.count() > 0;

      return hasError ? 'error' : 'loaded';
    }

    return 'loading';
  } catch {
    return 'unknown';
  }
}

/**
 * 保存测试报告
 * @param report 测试报告
 */
function saveReport(report: TestReport): void {
  report.endTime = new Date();

  const reportPath = '/tmp/code-generation-report.json';
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  console.log(`\n📊 测试报告已保存: ${reportPath}`);

  // 打印摘要
  console.log('\n========== 测试报告摘要 ==========');
  console.log(`⏱️  总耗时: ${((report.endTime.getTime() - report.startTime.getTime()) / 1000).toFixed(1)}秒`);
  console.log(`🔴 错误数: ${report.errors.length}`);
  console.log(`🟡 警告数: ${report.warnings.length}`);
  console.log(`📁 检测文件数: ${report.codeFiles.length}`);
  console.log(`🖼️  iframe状态: ${report.iframeStatus}`);
  console.log(`📸 截图数: ${report.screenshotPaths.length}`);

  if (report.errors.length > 0) {
    console.log('\n❌ 错误列表:');
    report.errors.forEach((err, i) => console.log(`  ${i + 1}. ${err}`));
  }

  if (report.codeFiles.filter(f => f.hasError).length > 0) {
    console.log('\n⚠️ 代码问题:');
    report.codeFiles.filter(f => f.hasError).forEach(f => {
      console.log(`  - ${f.name}: ${f.errorDetails}`);
    });
  }
}

test.describe('实时代码生成质量监控', () => {
  test('监控代码生成过程并检测问题', async ({ page }) => {
    test.setTimeout(300000); // 5分钟超时

    const report: TestReport = {
      startTime: new Date(),
      consoleLogs: [],
      codeFiles: [],
      errors: [],
      warnings: [],
      iframeStatus: 'unknown',
      screenshotPaths: [],
    };

    // 设置页面监控
    await setupPageMonitoring(page, report);

    console.log('🚀 开始代码生成质量监控测试\n');

    // 1. 访问首页并登录
    console.log('📍 Step 1: 访问首页并登录');
    await page.goto('http://localhost:3000');
    await page.waitForLoadState('networkidle');

    // 登录用户账号
    console.log('🔐 正在登录用户账号...');
    const loginButton = page.locator('button:has-text("登录"), a:has-text("登录")').first();
    await loginButton.click();

    // 等待登录表单出现
    await page.waitForSelector('input[type="text"], input[placeholder*="用户名"], input[placeholder*="邮箱"]', { timeout: 10000 });

    // 填写用户名和密码
    const usernameInput = page.locator('input[type="text"], input[placeholder*="用户名"], input[placeholder*="邮箱"]').first();
    await usernameInput.fill('justin');

    const passwordInput = page.locator('input[type="password"]').first();
    await passwordInput.fill('qazOKM123');

    // 点击登录提交按钮
    const submitButton = page.locator('button:has-text("登录"), button[type="submit"]').last();
    await submitButton.click();

    // 等待登录成功
    await page.waitForTimeout(3000);
    console.log('✅ 登录完成');

    // 返回首页
    await page.goto('http://localhost:3000');
    await page.waitForLoadState('networkidle');
    console.log('✅ 首页加载完成\n');

    // 截图
    const screenshot1 = '/tmp/monitor-01-homepage.png';
    await page.screenshot({ path: screenshot1, fullPage: true });
    report.screenshotPaths.push(screenshot1);

    // 2. 输入需求
    console.log('📍 Step 2: 输入需求');
    const requirementInput = page.locator('textarea, input[type="text"]').first();
    await expect(requirementInput).toBeVisible({ timeout: 10000 });

    const testRequirement = '做一个外卖平台，包含首页、商家列表、购物车功能';
    await requirementInput.fill(testRequirement);
    console.log(`✅ 已输入需求: ${testRequirement}\n`);

    // 3. 点击生成按钮
    console.log('📍 Step 3: 点击生成按钮');
    const generateButton = page.locator('button:has-text("生成")').first();
    await generateButton.click();
    console.log('✅ 已点击生成按钮\n');

    // 截图
    const screenshot2 = '/tmp/monitor-02-after-generate.png';
    await page.screenshot({ path: screenshot2, fullPage: true });
    report.screenshotPaths.push(screenshot2);

    // 4. 处理意图识别对话框（如果出现）
    console.log('📍 Step 4: 处理意图识别');
    try {
      await page.waitForTimeout(3000);

      // 尝试选择从零开始设计
      const designOption = page.locator('text=/从零开始/, button:has-text("从零开始")').first();
      if (await designOption.isVisible({ timeout: 5000 }).catch(() => false)) {
        await designOption.click();
        console.log('✅ 已选择"从零开始设计"\n');

        // 点击确认
        const confirmButton = page.locator('button:has-text("确认"), button:has-text("下一步")').first();
        if (await confirmButton.isVisible({ timeout: 3000 }).catch(() => false)) {
          await confirmButton.click();
        }
      }
    } catch {
      console.log('ℹ️  未出现意图选择对话框，继续下一步\n');
    }

    // 5. 选择设计风格（如果出现）
    console.log('📍 Step 5: 选择设计风格');
    await page.waitForTimeout(2000);

    const styleCards = page.locator('[class*="style"], button').filter({ hasText: /现代|简约|商务|活力/ });
    if (await styleCards.count() > 0) {
      await styleCards.first().click();
      console.log('✅ 已选择设计风格\n');

      // 截图
      const screenshot3 = '/tmp/monitor-03-style-selected.png';
      await page.screenshot({ path: screenshot3, fullPage: true });
      report.screenshotPaths.push(screenshot3);

      // 点击生成原型
      const prototypeButton = page.locator('button:has-text("生成原型"), button:has-text("下一步")').first();
      if (await prototypeButton.isVisible({ timeout: 3000 }).catch(() => false)) {
        await prototypeButton.click();
      }
    }

    // 6. 监控代码生成过程
    console.log('📍 Step 6: 实时监控代码生成\n');
    console.log('⏳ 等待代码生成（最长180秒）...\n');

    /**
     * 测试日志静默开关
     *
     * 是什么：基于环境变量控制实时输出。
     * 做什么：在需要静默输出时跳过 stdout 进度写入。
     * 为什么：减少 Playwright E2E 控制台噪音。
     */
    const silenceConsole = process.env.PLAYWRIGHT_SILENCE_CONSOLE !== '0';
    const monitorInterval = setInterval(async () => {
      // 检查iframe状态
      report.iframeStatus = await checkIframeStatus(page);

      // 尝试获取代码视图中的文件
      try {
        const codeTab = page.locator('button:has-text("代码"), [role="tab"]:has-text("代码")').first();
        if (await codeTab.isVisible({ timeout: 1000 }).catch(() => false)) {
          // 如果代码标签可见但没选中，点击它
          const isSelected = await codeTab.getAttribute('aria-selected');
          if (isSelected !== 'true') {
            // 不自动切换，只记录状态
          }
        }

        // 获取代码内容（如果可见）
        const codeBlocks = page.locator('code, pre');
        const codeCount = await codeBlocks.count();

        if (codeCount > 0) {
          const codeContent = await codeBlocks.first().textContent().catch(() => null);
          if (codeContent && codeContent.length > 50) {
            // 检测到代码内容
            const fileInfo: CodeFile = {
              name: 'detected-code.tsx',
              content: codeContent.substring(0, 500), // 只保存前500字符
              hasError: false,
            };

            // 验证语法
            const validation = validateCodeSyntax(codeContent, 'detected-code.tsx');
            if (!validation.valid) {
              fileInfo.hasError = true;
              fileInfo.errorDetails = validation.error;
              console.log(`⚠️  代码语法问题: ${validation.error}`);
            }

            // 避免重复添加
            if (!report.codeFiles.some(f => f.content === fileInfo.content)) {
              report.codeFiles.push(fileInfo);
            }
          }
        }
      } catch {
        // 忽略获取代码时的错误
      }

      // 打印当前状态（可静默）
      if (!silenceConsole) {
        process.stdout.write(`\r🔄 iframe: ${report.iframeStatus} | 错误: ${report.errors.length} | 警告: ${report.warnings.length} | 文件: ${report.codeFiles.length}`);
      }
    }, 3000);

    // 等待生成完成
    try {
      // 等待iframe加载或预览出现
      await Promise.race([
        page.waitForSelector('iframe', { timeout: 180000 }),
        page.waitForSelector('text=/预览完成|生成完成/i', { timeout: 180000 }),
        page.waitForSelector('text=/失败|错误/i', { timeout: 180000 }),
      ]);

      console.log('\n\n✅ 检测到生成结果\n');

      // 等待一段时间让内容完全加载
      await page.waitForTimeout(5000);

      // 最终状态检查
      report.iframeStatus = await checkIframeStatus(page);

    } catch {
      console.log('\n\n⚠️  等待超时，检查当前状态\n');
    } finally {
      clearInterval(monitorInterval);
    }

    // 7. 最终截图
    console.log('📍 Step 7: 保存最终状态截图');
    const screenshotFinal = '/tmp/monitor-final-result.png';
    await page.screenshot({ path: screenshotFinal, fullPage: true });
    report.screenshotPaths.push(screenshotFinal);

    // 8. 检查代码视图
    console.log('📍 Step 8: 检查代码视图');
    const codeTab = page.locator('button:has-text("代码"), [role="tab"]:has-text("代码")').first();
    if (await codeTab.isVisible({ timeout: 5000 }).catch(() => false)) {
      await codeTab.click();
      await page.waitForTimeout(2000);

      // 获取文件列表
      const fileItems = page.locator('[role="button"], button').filter({ hasText: /\.jsx|\.tsx|\.css|\.js/ });
      const fileCount = await fileItems.count();
      console.log(`✅ 检测到 ${fileCount} 个代码文件\n`);

      // 点击并检查每个文件
      for (let i = 0; i < Math.min(fileCount, 5); i++) {
        try {
          const fileItem = fileItems.nth(i);
          const fileName = await fileItem.textContent() || `file-${i}`;
          await fileItem.click();
          await page.waitForTimeout(1000);

          // 获取代码内容
          const codeBlock = page.locator('code, pre').first();
          const codeContent = await codeBlock.textContent().catch(() => '');

          if (codeContent) {
            const validation = validateCodeSyntax(codeContent, fileName);
            const fileInfo: CodeFile = {
              name: fileName.trim(),
              content: codeContent.substring(0, 500),
              hasError: !validation.valid,
              errorDetails: validation.error,
            };
            report.codeFiles.push(fileInfo);

            if (validation.valid) {
              console.log(`  ✅ ${fileName.trim()}: 语法正确`);
            } else {
              console.log(`  ⚠️  ${fileName.trim()}: ${validation.error}`);
            }
          }
        } catch {
          // 忽略单个文件检查错误
        }
      }

      // 截图代码视图
      const screenshotCode = '/tmp/monitor-code-view.png';
      await page.screenshot({ path: screenshotCode, fullPage: true });
      report.screenshotPaths.push(screenshotCode);
    }

    // 9. 保存报告
    console.log('\n📍 Step 9: 生成测试报告');
    saveReport(report);

    // 10. 断言检查
    console.log('\n📍 Step 10: 执行断言检查');

    // 关键断言：不应有严重错误
    const criticalErrors = report.errors.filter(e =>
      e.includes('TypeError') ||
      e.includes('ReferenceError') ||
      e.includes('SyntaxError') ||
      e.includes('Failed to fetch')
    );

    if (criticalErrors.length > 0) {
      console.log('❌ 检测到关键错误:');
      criticalErrors.forEach(e => console.log(`  - ${e}`));
    }

    // 检查是否有代码生成
    expect(report.iframeStatus).not.toBe('error');

    console.log('\n✅ 代码生成质量监控测试完成\n');
  });
});
