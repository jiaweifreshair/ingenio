"use client";

import { useState, useEffect, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { TopNav } from "@/components/layout/top-nav";
import { Footer } from "@/components/layout/footer";
import { HeroBanner } from "@/components/home/hero-banner";
import { ThreeAgentWorkflow } from "@/components/home/three-agent-workflow";
import { CoreFeatures } from "@/components/home/core-features";
import { TargetAudiences } from "@/components/home/target-audiences";
import { StepsShowcase } from "@/components/home/steps-showcase";
import { FAQAccordion } from "@/components/home/faq-accordion";
import { SmartWizard } from "@/components/home/smart-wizard";
import { FloatingActionBar } from "@/components/home/floating-action-bar";
import { GEOSnippet } from "@/components/home/geo-snippet";

// Smart Builder Components & Types
import { AppComplexitySelector } from "@/components/home/app-complexity-selector";
import { IndustrySelection } from "@/components/home/industry-selection";
import { AICapabilitySelector } from "@/components/home/ai-capability-selector";
import {
  type IndustryType,
  type AppComplexityMode,
  type AICapabilityType,
} from "@/types/smart-builder";
import { Loader2 } from "lucide-react";

function HomePageContent(): React.ReactElement {
  const searchParams = useSearchParams();
  const [requirement, setRequirement] = useState("");
  const [isWizardActive, setIsWizardActive] = useState(false);
  const [wizardPrompt, setWizardPrompt] = useState("");
  const [wizardContext, setWizardContext] = useState<{
    industry?: IndustryType | null;
    mode?: AppComplexityMode | null;
    capabilities?: AICapabilityType[];
  }>({});

  // Smart Builder State
  const [selectedIndustry, setSelectedIndustry] = useState<IndustryType | null>(null);
  const [selectedMode, setSelectedMode] = useState<AppComplexityMode | null>(null);
  const [selectedCapabilities, setSelectedCapabilities] = useState<AICapabilityType[]>([]);

  // Auto-launch wizard if query params exist
  useEffect(() => {
    const q = searchParams.get('q');
    const templateId = searchParams.get('template'); // Handle template query if needed
    
    if (q && !isWizardActive) {
      setWizardPrompt(q);
      setIsWizardActive(true);
      // Optional: Parse other params like 'industry', 'mode' if you want to support them via URL
      const industryParam = searchParams.get('industry') as IndustryType;
      const modeParam = searchParams.get('mode') as AppComplexityMode;
      
      if (industryParam || modeParam) {
          setWizardContext({
              industry: industryParam || null,
              mode: modeParam || null
          });
      }
    } else if (templateId && !isWizardActive) {
        // If template ID is present, we might want to start wizard with a specific prompt
        // For now, let's just use it as a prompt prefix or similar, 
        // or just rely on the user to input. 
        // But usually 'Use Template' implies auto-start.
        // Let's assume the template name is passed or we fetch it. 
        // For simplicity, if templateId is passed, we might need a generic prompt.
        const templateName = searchParams.get('templateName') || "Selected Template";
        setWizardPrompt(`Build an app based on template: ${templateName}`);
        setIsWizardActive(true);
    }
  }, [searchParams, isWizardActive]);

  const handleRequirementChange = (val: string) => {
    setRequirement(val);
  };

  const handleWizardActiveChange = (isActive: boolean) => {
    setIsWizardActive(isActive);
  };

  // Handler for Industry Selection
  const handleIndustrySelect = (id: IndustryType) => {
    setSelectedIndustry(prev => (prev === id ? null : id));
  };

  // Handler for App Mode Selection
  const handleModeSelect = (id: AppComplexityMode) => {
    setSelectedMode(prev => (prev === id ? null : id));
  };

  // Handler for AI Capabilities
  const handleCapabilityToggle = (id: AICapabilityType) => {
    setSelectedCapabilities(prev => {
      const exists = prev.includes(id);
      return exists ? prev.filter(c => c !== id) : [...prev, id];
    });
  };

  /**
   * Hero 输入框内下拉选择的直设回调
   * 用于与首页下方三个选择区块保持状态一致
   */
  const handleIndustryChange = (industry: IndustryType | null) => {
    setSelectedIndustry(industry);
  };

  const handleModeChange = (mode: AppComplexityMode | null) => {
    setSelectedMode(mode);
  };

  const handleCapabilitiesChange = (capabilities: AICapabilityType[]) => {
    setSelectedCapabilities(capabilities);
  };

  // Handler to remove tags from HeroBanner
  const handleRemoveTag = (type: 'INDUSTRY' | 'MODE' | 'CAPABILITY', id?: string) => {
    if (type === 'INDUSTRY') setSelectedIndustry(null);
    if (type === 'MODE') setSelectedMode(null);
    if (type === 'CAPABILITY' && id) {
      setSelectedCapabilities(prev => prev.filter(c => c !== id));
    }
  };

  // Handler for Example Selection (Clicking "示例" in complexity card)
  const handleExampleSelect = (mode: AppComplexityMode, prompt: string) => {
    setSelectedMode(mode);
    setRequirement(prompt);
    // Scroll to top to show Hero Banner input
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };
  
  // Handler for Launching Wizard from HeroBanner
  const handleLaunchWizard = (prompt: string, context: { industry: IndustryType | null, mode: AppComplexityMode | null, capabilities: AICapabilityType[] }) => {
    setWizardPrompt(prompt);
    setWizardContext(context);
    setIsWizardActive(true);
    // Scroll to top
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };
  
  // Return from Wizard
  const handleBackFromWizard = () => {
    setIsWizardActive(false);
    setWizardPrompt("");
    setWizardContext({});
    // clear search params from url without refresh if possible, or just leave them
    // It's cleaner to remove them so a refresh doesn't restart wizard
    window.history.pushState({}, '', '/');
  };

  // Derive hints (keep existing logic for compatibility if needed, but mainly we use the new props)
  let complexityHint: string | undefined;
  let techStackHint: string | undefined;

  if (selectedMode === 'ENTERPRISE') {
    complexityHint = 'COMPLEX';
    techStackHint = 'React + Spring Boot';
  } else if (selectedMode === 'WEB') {
    complexityHint = 'MEDIUM';
    techStackHint = 'React + Supabase';
  } else if (selectedMode === 'NATIVE') {
    complexityHint = 'COMPLEX';
    techStackHint = 'Kuikly + Spring Boot';
  }

  return (
    <div className="flex min-h-screen flex-col">
      {/* Floating Action Bar (Visible on Scroll) */}
      {!isWizardActive && (
        <FloatingActionBar
          requirement={requirement}
          onRequirementChange={handleRequirementChange}
          onLaunchWizard={handleLaunchWizard}
          selectedIndustry={selectedIndustry}
          selectedMode={selectedMode}
          selectedCapabilities={selectedCapabilities}
          onIndustryChange={handleIndustryChange}
          onModeChange={handleModeChange}
          onCapabilitiesChange={handleCapabilitiesChange}
        />
      )}

      {/* 顶部导航 */}
      <TopNav />

      {/* 主要内容 */}
      <main className="flex-1">
        
        {isWizardActive ? (
          /* Wizard Mode */
          <div className="pt-8 pb-16 px-4">
             <SmartWizard 
                initialRequirement={wizardPrompt}
                initialContext={wizardContext}
                onBack={handleBackFromWizard}
             />
          </div>
        ) : (
          /* Standard Homepage Mode */
          <>
            {/* Hero区域 */}
            <HeroBanner
              externalRequirement={requirement}
              onRequirementChange={handleRequirementChange}
              externalComplexityHint={complexityHint}
              externalTechStackHint={techStackHint}
              onWizardActiveChange={handleWizardActiveChange}
              // New Props for Smart Tags
              selectedIndustry={selectedIndustry}
              selectedMode={selectedMode}
              selectedCapabilities={selectedCapabilities}
              onIndustryChange={handleIndustryChange}
              onModeChange={handleModeChange}
              onCapabilitiesChange={handleCapabilitiesChange}
              onRemoveTag={handleRemoveTag}
              onLaunchWizard={handleLaunchWizard}
            />

            {/* GEO 核心摘要 (Machine & Human Friendly) */}
            <GEOSnippet />

            {/* 1. 应用复杂度选择 (New) */}
            <AppComplexitySelector
              selectedMode={selectedMode}
              onSelect={handleModeSelect}
              onSelectExample={handleExampleSelect}
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

/**
 * 秒构AI首页 (Suspense Wrapper)
 */
export default function HomePage(): React.ReactElement {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center bg-white dark:bg-black">
          <Loader2 className="h-8 w-8 animate-spin text-purple-600" />
        </div>
      }
    >
      <HomePageContent />
    </Suspense>
  );
}
