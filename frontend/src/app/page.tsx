"use client";

import { useState } from "react";
import { TopNav } from "@/components/layout/top-nav";
import { Footer } from "@/components/layout/footer";
import { HeroBanner } from "@/components/home/hero-banner";
import { ThreeAgentWorkflow } from "@/components/home/three-agent-workflow";
import { CoreFeatures } from "@/components/home/core-features";
import { TargetAudiences } from "@/components/home/target-audiences";
import { StepsShowcase } from "@/components/home/steps-showcase";
import { FAQAccordion } from "@/components/home/faq-accordion";

// Smart Builder Components & Types
import { AppComplexitySelector } from "@/components/home/app-complexity-selector";
import { IndustrySelection } from "@/components/home/industry-selection";
import { AICapabilitySelector } from "@/components/home/ai-capability-selector";
import {
  type IndustryType,
  type AppComplexityMode,
  type AICapabilityType,
  INDUSTRIES,
  APP_MODES,
  AI_CAPABILITIES
} from "@/types/smart-builder";

/**
 * 秒构AI首页
 * 产品介绍和核心功能展示
 */
export default function HomePage(): React.ReactElement {
  const [requirement, setRequirement] = useState("");
  const [isWizardActive, setIsWizardActive] = useState(false);

  // Smart Builder State
  const [selectedIndustry, setSelectedIndustry] = useState<IndustryType | null>(null);
  const [selectedMode, setSelectedMode] = useState<AppComplexityMode | null>(null);
  const [selectedCapabilities, setSelectedCapabilities] = useState<AICapabilityType[]>([]);

  const handleRequirementChange = (val: string) => {
    setRequirement(val);
  };

  const handleWizardActiveChange = (isActive: boolean) => {
    setIsWizardActive(isActive);
  };

  /**
   * Generates a smart prompt based on selections and updates the requirement
   */
  const updatePrompt = (
    industryId: IndustryType | null,
    modeId: AppComplexityMode | null,
    capabilities: AICapabilityType[]
  ) => {
    const industry = INDUSTRIES.find(i => i.id === industryId);
    const mode = APP_MODES.find(m => m.id === modeId);

    // Base prompt structure
    const promptParts = [];

    // 1. Industry Context
    if (industry) {
      if (industry.id === 'MORE') {
        promptParts.push("I want to build a custom application");
      } else {
        promptParts.push(`I want to build ${industry.promptContext}`);
      }
    } else {
      promptParts.push("I want to build an application");
    }

    // 2. App Mode / Tech Stack
    if (mode) {
      promptParts.push(`using ${mode.techStack} architecture (${mode.title})`);
    }

    // 3. AI Capabilities
    if (capabilities.length > 0) {
      const capsDesc = capabilities
        .map(capId => AI_CAPABILITIES.find(c => c.id === capId)?.promptDetail)
        .filter(Boolean)
        .join(", ");

      if (capsDesc) {
        promptParts.push(`featuring ${capsDesc}`);
      }
    }

    // Update requirement
    setRequirement(promptParts.join(" "));
  };

  // Handler for Industry Selection
  const handleIndustrySelect = (id: IndustryType) => {
    setSelectedIndustry(prev => {
      const newVal = prev === id ? null : id; // Toggle off if clicked again
      updatePrompt(newVal, selectedMode, selectedCapabilities);
      return newVal;
    });
  };

  // Handler for App Mode Selection
  const handleModeSelect = (id: AppComplexityMode) => {
    setSelectedMode(prev => {
      const newVal = prev === id ? null : id;
      updatePrompt(selectedIndustry, newVal, selectedCapabilities);
      return newVal;
    });
  };

  // Handler for AI Capabilities
  const handleCapabilityToggle = (id: AICapabilityType) => {
    setSelectedCapabilities(prev => {
      const exists = prev.includes(id);
      const newVal = exists
        ? prev.filter(c => c !== id)
        : [...prev, id];
      updatePrompt(selectedIndustry, selectedMode, newVal);
      return newVal;
    });
  };

  // Derive hints for HeroBanner
  let complexityHint: string | undefined;
  let techStackHint: string | undefined;

  if (selectedMode === 'ENTERPRISE') {
    complexityHint = 'COMPLEX';
    techStackHint = 'React + Spring Boot';
  } else if (selectedMode === 'WEB') {
    complexityHint = 'MEDIUM';
    techStackHint = 'React+Supabase';
  } else if (selectedMode === 'H5') {
    complexityHint = 'SIMPLE';
    techStackHint = 'H5+WebView';
  } else if (selectedMode === 'NATIVE') {
    // Native doesn't map directly to SIMPLE/MEDIUM/COMPLEX as defined in previous logic, 
    // but we can map it to COMPLEX or a new type. For now, let's say COMPLEX.
    complexityHint = 'COMPLEX';
    techStackHint = 'Kuikly';
  }

  return (
    <div className="flex min-h-screen flex-col">
      {/* 顶部导航 */}
      <TopNav />

      {/* 主要内容 */}
      <main className="flex-1">
        {/* Hero区域 */}
        <HeroBanner
          externalRequirement={requirement}
          onRequirementChange={handleRequirementChange}
          externalComplexityHint={complexityHint}
          externalTechStackHint={techStackHint}
          onWizardActiveChange={handleWizardActiveChange}
        />

        {/* 非Wizard模式下显示的内容 */}
        {!isWizardActive && (
          <>
            {/* 1. 应用复杂度选择 (New) */}
            <AppComplexitySelector
              selectedMode={selectedMode}
              onSelect={handleModeSelect}
            />

            {/* 2. 行业场景选择 (Replaces ScenarioSelection) */}
            <IndustrySelection
              selectedIndustry={selectedIndustry}
              onSelect={handleIndustrySelect}
            />

            {/* 3. AI 能力矩阵 (New) */}
            <AICapabilitySelector
              selectedCapabilities={selectedCapabilities}
              onToggle={handleCapabilityToggle}
            />

            {/* 三Agent智能工作流 (Keep) */}
            <ThreeAgentWorkflow />

            {/* 核心功能展示 (Keep) */}
            {/* <CoreFeatures /> -> Might be redundant with new sections, but keep for now if not explicitly asked to remove. 
                Wait, user asked "按照@[PRODUCT_UPGRADE_PLAN_SMART_BUILDER.md] 的定位进行优化".
                The plan says "首页将进行重大重构... CoreFeatures component will be replaced or significantly modified".
                Let's keep it for now as "Detailed Features" but maybe move it down or inspect if I should remove it.
                The plan implementation lists "CoreFeatures will be replaced...". 
                I will comment it out or keep it at the bottom. Let's keep it for now as general info.
            */}
            <CoreFeatures />

            {/* 目标用户组别 (Keep) */}
            <TargetAudiences />

            {/* 三步法 (Keep) */}
            <StepsShowcase />

            {/* 常见问题 (Keep) */}
            <FAQAccordion />
          </>
        )}
      </main>

      {/* 页脚 - 仅在非Wizard模式下显示 */}
      {!isWizardActive && <Footer />}

      {/* 锚点占位 */}
      <div id="start" className="hidden" aria-hidden="true" />
      <div id="demo" className="hidden" aria-hidden="true" />
    </div>
  );
}
