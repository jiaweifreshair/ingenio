/**
 * StepResultDisplay Step1「关键实体」展示测试
 *
 * 覆盖点：
 * - entities 为 JSON/类 JSON 字符串时，仅展示 description（中文描述）
 * - entities 为普通字符串时，仍原样展示
 *
 * 为什么：
 * - 页面展示应保持简洁可读，但上下文仍可保留完整 JSON 内容用于后续推理。
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LanguageProvider } from '@/contexts/LanguageContext';
import { StepResultDisplay } from '@/components/analysis/StepResultDisplay';
import type { StepResult } from '@/types/analysis-step-results';

describe('StepResultDisplay', () => {
  it('Step1：关键实体应优先展示中文 description', () => {
    const jsonLikeEntity = '{"name":"UserMoodRecord","description":"用户单次情绪记录实体","attributes":{"record_id","user_id"}}';
    const strictJsonEntity = JSON.stringify({
      name: 'ComfortLibrary',
      description: '预设的安慰语料库',
      attributes: ['content_id', 'category_tag', 'comfort_text', 'suggestion_text'],
    });

    const result: StepResult = {
      step: 1,
      data: {
        summary: '为学生群体打造一款轻量级的情绪记录与疗愈工具',
        entities: [jsonLikeEntity, strictJsonEntity, 'Blog'],
        actions: ['记录情绪', '提供安慰文案'],
        businessScenario: '学生日常学习与社交中的轻微焦虑缓解',
      },
    };

    render(
      <LanguageProvider>
        <StepResultDisplay
          result={result}
          onConfirm={vi.fn()}
          onModify={vi.fn()}
        />
      </LanguageProvider>
    );

    expect(screen.getByText('用户单次情绪记录实体')).toBeInTheDocument();
    expect(screen.getByText('预设的安慰语料库')).toBeInTheDocument();
    expect(screen.getByText('Blog')).toBeInTheDocument();

    // 原始 JSON 字符串不应直接渲染到页面上
    expect(screen.queryByText(/"name":"UserMoodRecord"/)).not.toBeInTheDocument();
  });
});

