import { G3Task, G3LogEntry, ArchitectOutput } from './types';
import { G3ContextLoader } from './context-loader';
import { LLMClient, MockLLMClient } from './llm-client';

export class G3Orchestrator {
  private loader: G3ContextLoader;
  private llm: LLMClient;
  private logs: G3LogEntry[] = [];
  public onLog: (entry: G3LogEntry) => void = () => {}; // Simple callback handler
  
  constructor(llmClient?: LLMClient) {
    this.loader = new G3ContextLoader();
    this.llm = llmClient || new MockLLMClient();
  }

  public log(role: G3LogEntry['role'], content: string, level: G3LogEntry['level'] = 'INFO') {
    const entry: G3LogEntry = {
      timestamp: Date.now(),
      role,
      step: 'ORCHESTRATOR',
      content,
      level
    };
    this.logs.push(entry);
    console.log(`[${role}] ${content}`);
    // Trigger callback
    this.onLog(entry);
  }

  async run(requirement: string): Promise<G3Task> {
    console.log('[DEBUG] Orchestrator.run called with:', requirement);
    const task: G3Task = {
      id: crypto.randomUUID(),
      requirement,
      status: 'PLANNING',
      rounds: 0,
      maxRounds: 3,
      artifacts: { codeFiles: {}, testFiles: {}, logs: [] }
    };

    this.log('SYSTEM', `G3 Engine Started. Requirement: ${requirement}`, 'INFO');

    try {
      // Phase 1: Architecture
      const design = await this.runArchitect(requirement);
      task.artifacts.designJson = design;
      
      // Enter the Game Loop
      let passed = false;
      while (task.rounds < task.maxRounds && !passed) {
        task.rounds++;
        this.log('SYSTEM', `Starting Round ${task.rounds}`, 'INFO');

        // Phase 2: Player (Blue)
        const code = await this.runPlayer(requirement, design, task.artifacts.logs);
        // 简单解析代码文件 (Mock)
        task.artifacts.codeFiles['Generated.java'] = code;

        // Phase 3: Coach (Red)
        const tests = await this.runCoach(requirement, code);
        task.artifacts.testFiles['AttackTest.java'] = tests;

        // Phase 4: Execution (Referee)
        const result = await this.runExecutor(code, tests);
        
        if (result.success) {
          passed = true;
          task.status = 'COMPLETED';
          this.log('SYSTEM', '🎉 Defense Successful! Code Ready for Delivery.', 'SUCCESS');
        } else {
          this.log('SYSTEM', `💥 Defense Failed. Vulnerabilities found: ${result.error}`, 'ERROR');
          task.artifacts.logs.push(`Round ${task.rounds} Failed: ${result.error}`);
          // Loop continues...
        }
      }

      if (!passed) {
        task.status = 'FAILED';
        this.log('SYSTEM', 'Max rounds reached. Code validation failed.', 'ERROR');
      }

    } catch (e) {
      this.log('SYSTEM', `Critical Error: ${(e as Error).message}`, 'ERROR');
      task.status = 'FAILED';
    }

    return task;
  }

  // --- Sub-routines ---

  private async runArchitect(requirement: string): Promise<ArchitectOutput> {
    this.log('ARCHITECT', 'Analyzing requirement...', 'INFO');
    const prompt = await this.loader.assemblePrompt('ARCHITECT', requirement);
    const res = await this.llm.chat([{ role: 'user', content: prompt }]);
    
    // 简单的 JSON 解析容错
    try {
        // 在真实场景中需要处理 Markdown 代码块包裹
        const jsonStr = res.content.replace(/```json/g, '').replace(/```/g, '');
        return JSON.parse(jsonStr);
    } catch {
        // Mock fallback - JSON 解析失败时返回空结构
        return { modules: [], dependencies: [] };
    }
  }

  private async runPlayer(req: string, design: ArchitectOutput, feedback: string[]): Promise<string> {
    this.log('PLAYER', 'Writing code...', 'INFO');
    const context = `Design: ${JSON.stringify(design)}\nFeedback: ${feedback.join('\n')}`;
    const prompt = await this.loader.assemblePrompt('PLAYER', req, context);
    const res = await this.llm.chat([{ role: 'user', content: prompt }]);
    return res.content;
  }

  private async runCoach(req: string, code: string): Promise<string> {
    this.log('COACH', 'Analyzing code for vulnerabilities...', 'INFO');
    const context = `Code to Attack:
${code}`;
    const prompt = await this.loader.assemblePrompt('COACH', req, context);
    const res = await this.llm.chat([{ role: 'user', content: prompt }]);
    return res.content;
  }

  private async runExecutor(_code: string, _tests: string): Promise<{ success: boolean; error?: string }> {
    this.log('SYSTEM', 'Running tests in sandbox...', 'INFO');
    
    // Mock Execution Logic
    // 在真实场景中，这里会调用后端 API 编译运行
    // 这里我们做一个随机模拟：50% 概率通过
    await new Promise(r => setTimeout(r, 1000)); // Simulate delay
    
    // 假设第一轮总是失败，第二轮成功 (为了演示效果)
    const isFirstRound = this.logs.filter(l => l.content.includes('Starting Round 1')).length > 0 && 
                         this.logs.filter(l => l.content.includes('Starting Round 2')).length === 0;

    if (isFirstRound) {
        return { success: false, error: "SQL Injection vulnerability detected in query parameters." };
    }
    
    return { success: true };
  }
}
