/**
 * Mock数据演示页面 - 展示步骤结果UI
 */
'use client';

import React, { useState } from 'react';
import { StepResultDisplay } from '@/components/analysis/StepResultDisplay';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { mockStepResults } from '@/lib/mock/step-results.mock';

export default function MockDemoPage() {
  const [currentStep, setCurrentStep] = useState<number>(1);
  const [confirmedSteps, setConfirmedSteps] = useState<Set<number>>(new Set());

  const currentResult = mockStepResults.find(r => r.step === currentStep);

  const handleConfirm = () => {
    setConfirmedSteps(prev => new Set([...prev, currentStep]));
    if (currentStep < 5) {
      setCurrentStep(currentStep + 1);
    }
  };

  const handleModify = () => {
    alert(`修改 Step ${currentStep} 的内容`);
  };

  const handleReset = () => {
    setCurrentStep(1);
    setConfirmedSteps(new Set());
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-950 dark:to-slate-900 p-8">
      <div className="max-w-5xl mx-auto space-y-6">
        {/* 页面标题 */}
        <div className="text-center space-y-2">
          <h1 className="text-4xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
            AI分析步骤结果展示 - Mock演示
          </h1>
          <p className="text-muted-foreground">
            展示Step 1-5的分析结果UI设计
          </p>
        </div>

        {/* 步骤导航 */}
        <Card className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">步骤导航</h2>
            <Button variant="outline" size="sm" onClick={handleReset}>
              重置演示
            </Button>
          </div>
          <div className="flex gap-2">
            {[1, 2, 3, 4, 5].map((step) => (
              <Button
                key={step}
                variant={currentStep === step ? 'default' : confirmedSteps.has(step) ? 'secondary' : 'outline'}
                onClick={() => setCurrentStep(step)}
                className="flex-1"
              >
                {confirmedSteps.has(step) ? '✓ ' : ''}Step {step}
              </Button>
            ))}
          </div>
        </Card>

        {/* 当前步骤结果展示 */}
        {currentResult && (
          <StepResultDisplay
            result={currentResult}
            onConfirm={handleConfirm}
            onModify={handleModify}
            loading={false}
          />
        )}

        {/* 完成提示 */}
        {confirmedSteps.size === 5 && (
          <Card className="p-6 bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800">
            <div className="text-center space-y-2">
              <h3 className="text-xl font-semibold text-green-700 dark:text-green-300">
                🎉 所有步骤已完成！
              </h3>
              <p className="text-sm text-muted-foreground">
                您已经确认了所有5个分析步骤的结果
              </p>
              <Button onClick={handleReset} className="mt-4">
                重新开始演示
              </Button>
            </div>
          </Card>
        )}

        {/* 设计说明 */}
        <Card className="p-6 bg-blue-50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-800">
          <h3 className="text-lg font-semibold mb-3">UI/UX设计特点</h3>
          <ul className="space-y-2 text-sm">
            <li className="flex items-start gap-2">
              <span className="text-blue-600">✓</span>
              <span><strong>信息层次清晰</strong>：使用标题、图标、分组来组织信息</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-600">✓</span>
              <span><strong>视觉吸引力强</strong>：每个步骤使用不同的主题色，增强识别度</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-600">✓</span>
              <span><strong>交互流畅</strong>：明确的&quot;确认&quot;和&quot;修改&quot;按钮，用户掌控流程</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-600">✓</span>
              <span><strong>响应式设计</strong>：适配不同屏幕尺寸</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-600">✓</span>
              <span><strong>深色模式支持</strong>：完整的深色模式适配</span>
            </li>
          </ul>
        </Card>
      </div>
    </div>
  );
}
