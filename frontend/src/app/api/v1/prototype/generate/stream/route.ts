import { NextRequest, NextResponse } from 'next/server';
import { createOpenAI } from '@ai-sdk/openai';
import { streamText } from 'ai';

// 强制动态路由
export const dynamic = 'force-dynamic';
export const maxDuration = 300; // 5分钟超时

/**
 * 环境变量获取
 */
const API_KEY = process.env.DEEPSEEK_API_KEY || process.env.OPENAI_API_KEY || '';
const BASE_URL = process.env.DEEPSEEK_BASE_URL || 'https://api.deepseek.com/v1'; // 默认 DeepSeek

// 创建 AI Provider
const aiProvider = createOpenAI({
  name: 'deepseek',
  apiKey: API_KEY,
  baseURL: BASE_URL,
});

// 系统提示词模板
const SYSTEM_PROMPT_TEMPLATE = `
You are an expert full-stack developer and architect.
Your goal is to generate high-quality, production-ready code for a web application.

CRITICAL RULES:
1. **NO TRUNCATION**: You must generate COMPLETE files. Never leave code unfinished.
2. **FILE FORMAT**: You must output files using the XML format:
   <file path="src/components/Header.tsx">
   // code content...
   </file>
3. **TECH STACK**: Use React, Tailwind CSS, and Lucide React icons.
4. **IMPORTS**: All imports must be at the top of the file.
5. **COMPLETENESS**: Do not use "..." placeholders. Implement full functionality.

If the output is too long, prioritizing completing the current file before stopping.
`;

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const {
      userRequirement,
      designStyle,
      context,
      mode = 'code' // 'plan' | 'code'
    } = body;

    if (!userRequirement) {
      return NextResponse.json({ error: 'User requirement is required' }, { status: 400 });
    }

    // ==========================================
    // Mode: PLAN (生成技术方案)
    // ==========================================
    if (mode === 'plan') {
      const planPrompt = `
Analyze the following requirement and generate a detailed technical blueprint.

Requirement: ${userRequirement}
Style: ${designStyle || 'Default'}

Output the blueprint in Markdown format with the following sections:
1. **Architecture Overview**
2. **Key Features & Logic**
3. **Data Model (Entities)**
4. **File Structure Plan** (List critical files)
5. **Implementation Steps**

Make it professional, clear, and actionable.
`;

      const result = await streamText({
        model: aiProvider('deepseek-chat'), // 或 deepseek-reasoner
        messages: [
          { role: 'system', content: 'You are a technical architect.' },
          { role: 'user', content: planPrompt }
        ],
        temperature: 0.7,
      });

      return result.toTextStreamResponse();
    }

    // ==========================================
    // Mode: CODE (生成代码 - 防截断逻辑)
    // ==========================================

    // 构建完整提示词
    const fullPrompt = `
User Requirement: ${userRequirement}
Design Style: ${designStyle || 'Modern Clean'}

${context?.plan ? `Technical Plan:\n${context.plan}\n\n` : ''}

Generate the application code based on the requirement (and plan if provided).
Start with 'src/App.tsx' and 'src/index.css'.
Generate all necessary components in 'src/components/'.
`;

    // 状态追踪
    let generatedCode = '';
    let loopCount = 0;
    const maxLoops = 5;
    let continueGeneration = false;

    // 创建流
    const encoder = new TextEncoder();
    const stream = new TransformStream();
    const writer = stream.writable.getWriter();

    (async () => {
      try {
        do {
          loopCount++;
          console.log(`[Generate] Loop ${loopCount}/${maxLoops}`);

          // 构建消息历史
          const messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string }> = [
            { role: 'system', content: SYSTEM_PROMPT_TEMPLATE },
            { role: 'user', content: fullPrompt }
          ];

          // 如果是续写，将之前的生成结果作为 assistant 消息加入
          // 构建更为明确的续写提示词
          const lastChars = generatedCode.slice(-100);
          messages.push({ role: 'assistant', content: generatedCode });
          messages.push({
            role: 'user',
            content: `The previous response was truncated. Please continue EXACTLY from where you left off. 
              
              CONTEXT: The last 100 characters of your output were:
              "${lastChars}"
              
              INSTRUCTIONS:
              1. Do NOT repeat the code content above.
              2. Do NOT output the file header (<file...>) or imports again unless starting a NEW file.
              3. If you were in the middle of a file, just continue the code logic immediately.
              4. Continue the code stream seamlessly.`
          });

          const result = await streamText({
            model: aiProvider('deepseek-chat'),
            messages: messages,
            temperature: 0.7,
          });

          // 读取流并转发
          let _chunkText = '';
          for await (const chunk of result.textStream) {
            generatedCode += chunk;
            _chunkText += chunk;
            // 实时发送给前端
            await writer.write(encoder.encode(chunk));
          }

          // 截断检测逻辑 (借鉴 open-lovable-cn)
          const finishReason = await result.finishReason;
          let quickTruncationDetected = false;

          // 1. 检查文件标签闭合
          const fileOpenCount = (generatedCode.match(/<file path=\"/g) || []).length;
          const fileCloseCount = (generatedCode.match(/<\/file>/g) || []).length;

          if (fileOpenCount > fileCloseCount) {
            quickTruncationDetected = true;
            console.warn(`[Generate] Truncation detected: Unclosed file tags (${fileOpenCount} vs ${fileCloseCount})`);
          }

          // 2. 检查代码末尾是否有明显截断符号
          const trimmedCode = generatedCode.trim();
          if (trimmedCode.endsWith(',') || trimmedCode.endsWith('{') || trimmedCode.endsWith('(') || trimmedCode.endsWith('=')) {
            quickTruncationDetected = true;
          }

          // 决定是否继续
          if ((finishReason === 'length' || quickTruncationDetected) && loopCount < maxLoops) {
            continueGeneration = true;
            await writer.write(encoder.encode('\n\n[SYSTEM: Continuing generation...]\n\n'));
          } else {
            continueGeneration = false;
          }

        } while (continueGeneration);

      } catch (error) {
        console.error('[Generate] Error:', error);
        await writer.write(encoder.encode(`\n[ERROR: ${(error as Error).message}]`));
      } finally {
        await writer.close();
      }
    })();

    return new Response(stream.readable, {
      headers: {
        'Content-Type': 'text/plain; charset=utf-8',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
      },
    });

  } catch (error) {
    return NextResponse.json({ error: (error as Error).message }, { status: 500 });
  }
}