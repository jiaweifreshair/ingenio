"use client";

import { useState, useEffect, useRef } from "react";

import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";

import { Textarea } from "@/components/ui/textarea";

import { Badge } from "@/components/ui/badge";



import { LoginDialog } from "@/components/auth/login-dialog";

import { useToast } from "@/hooks/use-toast";

import {

  Sparkles,

  // TODO: 后续添加功能时取消注释
  // Type,

  // TODO: 后续添加功能时取消注释以下导入
  // Image as ImageIcon,
  // Mic,
  // FileText,

  Link as LinkIcon,

  Layout,

  Zap,

  Briefcase,

  Wand2,

  ChevronDown,

  X,

  Plus

} from "lucide-react";

import {

  type IndustryType,

  type AppComplexityMode,

  type AICapabilityType,

  INDUSTRIES,

  APP_MODES,

  AI_CAPABILITIES

} from "@/types/smart-builder";

import { cn } from "@/lib/utils";

// TODO: 后续添加输入模式切换功能时取消注释
// import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { useLanguage } from "@/contexts/LanguageContext";

// Removed unused Select imports

import {

  DropdownMenu,

  DropdownMenuCheckboxItem,

  DropdownMenuContent,

  // DropdownMenuItem, // Removed as it is unused

  DropdownMenuLabel,

  DropdownMenuSeparator,

  DropdownMenuTrigger,

} from "@/components/ui/dropdown-menu";



/**

 * 输入模式枚举
 * TODO: 后续添加功能时取消注释

 */

// type InputMode = 'TEXT' | 'IMAGE' | 'AUDIO' | 'DOC' | 'LINK';



/**
 * 生成模式枚举 (New Idea vs Redesign)
 */
type GenerationMode = 'NEW_IDEA' | 'REDESIGN_SITE';



/**

 * V2.0 向导流程步骤枚举 (Legacy - now we redirect)

 */

type WizardStep = 'IDLE' | 'REDIRECTING';



/**

 * HeroBanner组件 - 首页Hero区域 (Redesigned)

 */

export function HeroBanner({

  externalRequirement,

  onRequirementChange,

  externalComplexityHint: _externalComplexityHint,

  externalTechStackHint: _externalTechStackHint,

  onWizardActiveChange,

  selectedIndustry,

  selectedMode: externalSelectedMode,

  selectedCapabilities: externalSelectedCapabilities,

  onIndustryChange,

  onModeChange,

    onCapabilitiesChange,

    onRemoveTag: _onRemoveTag,

    onLaunchWizard

  }: {

    externalRequirement?: string;

    onRequirementChange?: (val: string) => void;

    externalComplexityHint?: string;

    externalTechStackHint?: string;

    onWizardActiveChange?: (isActive: boolean) => void;

    selectedIndustry?: IndustryType | null;

    selectedMode?: AppComplexityMode | null;

    selectedCapabilities?: AICapabilityType[];

    onIndustryChange?: (industry: IndustryType | null) => void;

    onModeChange?: (mode: AppComplexityMode | null) => void;

    onCapabilitiesChange?: (capabilities: AICapabilityType[]) => void;

    onRemoveTag?: (type: 'INDUSTRY' | 'MODE' | 'CAPABILITY', id?: string) => void;

        onLaunchWizard?: (prompt: string, context: { industry: IndustryType | null, mode: AppComplexityMode | null, capabilities: AICapabilityType[] }) => void;

    

      } = {}): React.ReactElement {

    

        const router = useRouter();

        const { t } = useLanguage();

    

        const { toast } = useToast();

      

        // --- State: Generation Mode ---

    

        const [generationMode, setGenerationMode] = useState<GenerationMode>('NEW_IDEA');

    

        // Define INPUT_MODES inside component to use translations

        // TODO: 后续添加输入模式切换功能时取消注释
        // 暂时只保留文本输入模式，其他模式（图片、语音、文档、链接）后续再加
        // const inputModes: { mode: InputMode; label: string; icon: React.ReactNode }[] = [
        //   { mode: 'TEXT', label: t('hero.input_mode_text'), icon: <Type className="w-4 h-4" /> },
        //   { mode: 'IMAGE', label: t('hero.input_mode_image'), icon: <ImageIcon className="w-4 h-4" /> },
        //   { mode: 'AUDIO', label: t('hero.input_mode_audio'), icon: <Mic className="w-4 h-4" /> },
        //   { mode: 'DOC', label: t('hero.input_mode_doc'), icon: <FileText className="w-4 h-4" /> },
        //   { mode: 'LINK', label: t('hero.input_mode_link'), icon: <LinkIcon className="w-4 h-4" /> },
        // ];

    

  

    // --- State: Internal Selections (if not controlled externally, though props suggest they might be) ---

    const [localIndustry, setLocalIndustry] = useState<IndustryType | null>(selectedIndustry || null);

    const [localMode, setLocalMode] = useState<AppComplexityMode | null>(externalSelectedMode || null);

    const [localCapabilities, setLocalCapabilities] = useState<AICapabilityType[]>(externalSelectedCapabilities || []);

  

    // Update local state when props change

    useEffect(() => {

      if (selectedIndustry !== undefined) setLocalIndustry(selectedIndustry);

      if (externalSelectedMode !== undefined) setLocalMode(externalSelectedMode);

      if (externalSelectedCapabilities !== undefined) setLocalCapabilities(externalSelectedCapabilities);

    }, [selectedIndustry, externalSelectedMode, externalSelectedCapabilities]);

  

  

    // --- State: Wizard Logic (Legacy - Simplified) ---

    const [wizardStep, setWizardStep] = useState<WizardStep>('IDLE');

    const wizardStepRef = useRef<WizardStep>(wizardStep);

    useEffect(() => { wizardStepRef.current = wizardStep; }, [wizardStep]);

  

    useEffect(() => {

      onWizardActiveChange?.(wizardStep !== 'IDLE');

    }, [wizardStep, onWizardActiveChange]);

    

    // TODO: 后续添加输入模式切换功能时取消注释
    // const [inputMode, setInputMode] = useState<InputMode>('TEXT');

  

    // Local requirement state

    const [requirement, setRequirement] = useState('');

  

    // Sync external requirement

    useEffect(() => {

      if (externalRequirement !== undefined && requirement !== externalRequirement) {

        setRequirement(externalRequirement);

      }

    }, [externalRequirement]);

  

    const [loginDialogOpen, setLoginDialogOpen] = useState(false);

  

    // Input References

    const cloneUrlInputRef = useRef<HTMLInputElement>(null);

  

        /**

  

         * Constructs the full prompt

  

         */

  

                const getFullPrompt = () => {

  

          

  

                  if (generationMode === 'REDESIGN_SITE') {

  

          

  

                    const url = cloneUrlInputRef.current?.value || '';

  

                    const userReq = requirement.trim() ? `\nUser Instructions: ${requirement}` : '';

  

          

  

                    return `Redesign/Refactor the website at this URL: ${url}.${userReq}`;

  

          

  

                  }

  

      const parts = [];

  

      // 1. Tags Context (Use local state)

      if (localIndustry) {

        const industry = INDUSTRIES.find(i => i.id === localIndustry);

        if (industry) parts.push(`Industry: ${industry.label} (${industry.promptContext}).`);

      }

      if (localMode) {

        const mode = APP_MODES.find(m => m.id === localMode);

        if (mode) parts.push(`App Mode: ${mode.title} (${mode.techStack}).`);

      }

      if (localCapabilities && localCapabilities.length > 0) {

        const caps = localCapabilities.map(id => AI_CAPABILITIES.find(c => c.id === id)?.label).join(", ");

        const capsDetail = localCapabilities.map(id => AI_CAPABILITIES.find(c => c.id === id)?.promptDetail).join("; ");

        parts.push(`AI Capabilities: ${caps}. Features: ${capsDetail}.`);

      }

  

      // 2. User Text

      if (requirement.trim()) {

        parts.push(`Requirement Description: ${requirement}`);

      } else {

        if (parts.length > 0) parts.push("Please build this application.");

      }

  

      return parts.join("\n");

    };

  

    const fullPrompt = getFullPrompt();

  

    const handleSubmitRequirement = () => {
      // 重要：在提交时重新计算prompt，确保获取最新的input值
      const currentPrompt = getFullPrompt();

      if (generationMode === 'REDESIGN_SITE') {

        const url = cloneUrlInputRef.current?.value;

        if (!url) {

          toast({ title: t('hero.toast_input_url'), description: t('hero.toast_input_url_desc'), variant: "destructive" });

          return;

        }

      } else {

        if (!currentPrompt.trim()) {

          toast({ title: t('hero.toast_input_req'), description: t('hero.toast_input_req_desc'), variant: "destructive" });

          return;

        }

      }




      // Call parent handler to start wizard

      if (onLaunchWizard) {

        onLaunchWizard(currentPrompt, {

          industry: localIndustry,

          mode: localMode,

          capabilities: localCapabilities

        });

      } else {

        // Fallback: Legacy Redirect (should not happen in new flow)

        // V2 Integration: Redirect to /create-v2

        setWizardStep('REDIRECTING');

        

        // Construct query parameters

        const params = new URLSearchParams();

        params.set('q', fullPrompt);

        

        if (localIndustry) params.set('industry', localIndustry);

        if (localMode) params.set('mode', localMode);

        

              // Use router to push

        

              router.push(`/?${params.toString()}`);

      }

    };



  /**

   * 切换本地 AI 能力选择，并同步到父组件

   */

  const toggleLocalCapability = (capId: AICapabilityType) => {
    setLocalCapabilities(prev => {
      const exists = prev.includes(capId);
      return exists ? prev.filter(c => c !== capId) : [...prev, capId];
    });
  };

  // 使用 useEffect 同步 capabilities 变化到父组件，避免渲染期间 setState
  // 只有当本地状态与外部状态不同时才同步，避免无限循环
  useEffect(() => {
    const externalStr = JSON.stringify(externalSelectedCapabilities || []);
    const localStr = JSON.stringify(localCapabilities);
    if (localStr !== externalStr) {
      onCapabilitiesChange?.(localCapabilities);
    }
  }, [localCapabilities, externalSelectedCapabilities, onCapabilitiesChange]);



  /**

   * 阻止 DropdownMenuTrigger 在点击清除按钮时误触发

   */

  const stopDropdownTrigger = (e: React.PointerEvent<HTMLButtonElement>) => {

    e.preventDefault();

    e.stopPropagation();

  };

  // --- Helper: Render the 3 Selectors (Dropdown inside input) ---
  const renderSelectors = () => {
    const selectedModeConfig = localMode ? APP_MODES.find(m => m.id === localMode) : null;
    const selectedIndustryConfig = localIndustry ? INDUSTRIES.find(i => i.id === localIndustry) : null;

    return (
      <div className="flex flex-wrap items-center gap-3 animate-in fade-in duration-300">

        {/* 1. 技术选型 / 模式 */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <div
              role="button"
              tabIndex={0}
              className={cn(
                "flex items-center gap-2 px-3 py-1.5 rounded-full border transition-all duration-300 h-9 group outline-none cursor-pointer",
                localMode
                  ? "bg-white dark:bg-slate-800 border-blue-200 dark:border-blue-800 shadow-sm text-foreground pr-2"
                  : "bg-white/60 dark:bg-white/5 border-transparent hover:bg-white hover:shadow-sm text-muted-foreground"
              )}
            >
              <div
                className={cn(
                  "p-1.5 rounded-full text-white shrink-0",
                  selectedModeConfig?.colorClass ?? "bg-slate-300"
                )}
              >
                <Layout className="w-3 h-3" />
              </div>
              <span className="text-sm font-medium whitespace-nowrap">
                {selectedModeConfig ? t(`const.mode.${selectedModeConfig.id}.title`) : t('hero.selector_tech')}
              </span>

              {localMode ? (
                <button
                  type="button"
                  aria-label="清除技术选型"
                  className="ml-1 p-0.5 rounded-full hover:bg-slate-200 dark:hover:bg-slate-700 text-muted-foreground transition-colors cursor-pointer"
                  onPointerDown={stopDropdownTrigger}
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    setLocalMode(null);
                    onModeChange?.(null);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      e.stopPropagation();
                      setLocalMode(null);
                      onModeChange?.(null);
                    }
                  }}
                >
                  <X className="w-3 h-3" />
                </button>
              ) : (
                <ChevronDown className="w-3.5 h-3.5 opacity-60" />
              )}
            </div>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            <DropdownMenuLabel>{t('hero.selector_tech')}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            {APP_MODES.map(mode => (
              <DropdownMenuCheckboxItem
                key={mode.id}
                checked={localMode === mode.id}
                disabled={mode.disabled}
                onCheckedChange={() => {
                  if (mode.disabled) return;
                  const next = mode.id;
                  setLocalMode(next);
                  onModeChange?.(next);
                }}
              >
                {t(`const.mode.${mode.id}.title`)} · {mode.techStack}
              </DropdownMenuCheckboxItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>

        {/* 2. 应用场景 / 行业 */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <div
              role="button"
              tabIndex={0}
              className={cn(
                "flex items-center gap-2 px-3 py-1.5 rounded-full border transition-all duration-300 h-9 group outline-none cursor-pointer",
                localIndustry
                  ? "bg-white dark:bg-slate-800 border-emerald-200 dark:border-emerald-800 shadow-sm text-foreground pr-2"
                  : "bg-white/60 dark:bg-white/5 border-transparent hover:bg-white hover:shadow-sm text-muted-foreground"
              )}
            >
              <div
                className={cn(
                  "p-1.5 rounded-full text-white shrink-0",
                  localIndustry ? "bg-emerald-500" : "bg-slate-300"
                )}
              >
                <Briefcase className="w-3 h-3" />
              </div>
              <span className="text-sm font-medium whitespace-nowrap">
                {selectedIndustryConfig ? (t(`const.industry.${selectedIndustryConfig.id}.label`) || t('hero.more_scenarios')) : t('hero.selector_scenario')}
              </span>

              {localIndustry ? (
                <button
                  type="button"
                  aria-label="清除应用场景"
                  className="ml-1 p-0.5 rounded-full hover:bg-slate-200 dark:hover:bg-slate-700 text-muted-foreground transition-colors cursor-pointer"
                  onPointerDown={stopDropdownTrigger}
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    setLocalIndustry(null);
                    onIndustryChange?.(null);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      e.stopPropagation();
                      setLocalIndustry(null);
                      onIndustryChange?.(null);
                    }
                  }}
                >
                  <X className="w-3 h-3" />
                </button>
              ) : (
                <ChevronDown className="w-3.5 h-3.5 opacity-60" />
              )}
            </div>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" className="h-64 overflow-y-auto">
            <DropdownMenuLabel>{t('hero.selector_scenario')}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            {INDUSTRIES.map(industry => (
              <DropdownMenuCheckboxItem
                key={industry.id}
                checked={localIndustry === industry.id}
                onCheckedChange={() => {
                  const next = industry.id;
                  setLocalIndustry(next);
                  onIndustryChange?.(next);
                }}
              >
                {t(`const.industry.${industry.id}.label`)}
              </DropdownMenuCheckboxItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>

        {/* 3. AI 能力 (多选) - 展示为标签列表 */}
        {localCapabilities.map((capId) => {
            // const cap = AI_CAPABILITIES.find(c => c.id === capId);
            return (
                <Badge 
                    key={capId} 
                    variant="secondary"
                    className="h-9 px-3 py-1.5 flex items-center gap-1.5 whitespace-nowrap bg-purple-50 text-purple-700 border border-purple-200 hover:bg-purple-100 dark:bg-purple-900/30 dark:text-purple-300 dark:border-purple-800 rounded-full text-sm font-medium"
                >
                    {t(`const.ai.${capId}.label`)}
                    <div 
                        role="button"
                        onClick={(e) => {
                            e.stopPropagation();
                            toggleLocalCapability(capId);
                        }}
                        className="ml-0.5 hover:bg-purple-200 dark:hover:bg-purple-800 rounded-full p-0.5 transition-colors cursor-pointer"
                    >
                        <X className="w-3 h-3" />
                    </div>
                </Badge>
            );
        })}

        {/* AI 能力 Dropdown Trigger (Plus Button or Label) */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <div
              role="button"
              tabIndex={0}
              className={cn(
                "flex items-center gap-2 px-3 py-1.5 rounded-full border transition-all duration-300 h-9 group outline-none cursor-pointer",
                localCapabilities.length > 0
                  ? "bg-slate-100 dark:bg-slate-800 border-transparent text-muted-foreground w-9 px-0 justify-center" // Compact plus button
                  : "bg-white/60 dark:bg-white/5 border-transparent hover:bg-white hover:shadow-sm text-muted-foreground" // Full label
              )}
            >
              {localCapabilities.length > 0 ? (
                 <Plus className="w-4 h-4" />
              ) : (
                <>
                    <div className="p-1.5 rounded-full text-white shrink-0 bg-slate-300">
                        <Zap className="w-3 h-3" />
                    </div>
                    <span className="text-sm font-medium whitespace-nowrap">{t('hero.selector_ai')}</span>
                    <ChevronDown className="w-3.5 h-3.5 opacity-60" />
                </>
              )}
            </div>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" className="w-64">
            <DropdownMenuLabel>{t('hero.selector_ai')}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            {AI_CAPABILITIES.map(cap => (
              <DropdownMenuCheckboxItem
                key={cap.id}
                checked={localCapabilities.includes(cap.id)}
                onCheckedChange={() => toggleLocalCapability(cap.id)}
              >
                {t(`const.ai.${cap.id}.label`)}
              </DropdownMenuCheckboxItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>

      </div>
    );
  };

  // --- Step Rendering (Legacy removed - only IDLE state remains) ---

  // --- IDLE State (The Redesigned Hero) ---
  return (
    <div className="relative w-full overflow-hidden bg-slate-50 dark:bg-black font-sans selection:bg-purple-100 selection:text-purple-900">

      {/* Background Gradients */}
      <div className="absolute top-0 left-0 w-full h-[600px] bg-gradient-to-b from-purple-50/50 to-transparent dark:from-purple-900/10 dark:to-transparent pointer-events-none" />
      <div className="absolute top-[-10%] right-[-5%] w-[500px] h-[500px] rounded-full bg-blue-100/40 dark:bg-blue-900/10 blur-3xl pointer-events-none" />
      <div className="absolute bottom-[10%] left-[-10%] w-[600px] h-[600px] rounded-full bg-pink-100/40 dark:bg-pink-900/10 blur-3xl pointer-events-none" />

      {/* Login Dialog */}
      <LoginDialog open={loginDialogOpen} onOpenChange={setLoginDialogOpen} />

      <div className="relative z-10 container mx-auto px-4 pt-16 pb-16 flex flex-col items-center">

        {/* Main Title */}
        <h1 className="text-3xl md:text-5xl font-bold text-center tracking-tight text-slate-900 dark:text-slate-50 mb-6 bg-clip-text text-transparent bg-gradient-to-r from-slate-900 to-slate-600 dark:from-white dark:to-slate-400">
          {t('hero.title_part1')}{t('hero.title_part2')}
          <span className="text-xl md:text-3xl font-normal text-slate-500 dark:text-slate-400 mt-4 block">
            {t('hero.subtitle')}
          </span>
        </h1>

        {/* --- Main Interaction Card --- */}
        <div className="w-full max-w-4xl mt-4">

          {/* 1. Top Tabs / Mode Switcher */}
          <div className="flex items-center gap-1 p-1 bg-slate-200/50 dark:bg-slate-800/50 rounded-t-2xl w-fit mx-auto backdrop-blur-sm border border-b-0 border-white/20">
            <button
              onClick={() => setGenerationMode('NEW_IDEA')}
              className={cn(
                "px-4 py-1.5 rounded-xl text-sm font-medium transition-all duration-200 flex items-center gap-2",
                generationMode === 'NEW_IDEA'
                  ? "bg-white dark:bg-slate-900 shadow-sm text-foreground scale-[1.02]"
                  : "text-muted-foreground hover:text-foreground hover:bg-white/50 dark:hover:bg-white/10"
              )}
            >
              <Sparkles className="w-4 h-4 text-amber-500" /> {t('hero.mode_create')}
            </button>
            <button
              onClick={() => setGenerationMode('REDESIGN_SITE')}
              className={cn(
                "px-4 py-1.5 rounded-xl text-sm font-medium transition-all duration-200 flex items-center gap-2",
                generationMode === 'REDESIGN_SITE'
                  ? "bg-white dark:bg-slate-900 shadow-sm text-foreground scale-[1.02]"
                  : "text-muted-foreground hover:text-foreground hover:bg-white/50 dark:hover:bg-white/10"
              )}
            >
              <Wand2 className="w-4 h-4 text-purple-500" /> {t('hero.mode_redesign')}
            </button>
          </div>

          {/* 2. Input Canvas */}
          <div className="relative w-full bg-white dark:bg-slate-900 rounded-[2rem] p-2 shadow-2xl border border-white/20 ring-1 ring-black/5 dark:ring-white/10">
            <div className="relative min-h-[220px] flex flex-col">

              <div className="flex-1 p-6 flex flex-col animate-in fade-in zoom-in-95">
                
                {/* 模式特定的顶部控件 */}
                <div className="mb-4 space-y-4">
                  
                  {/* NEW_IDEA 模式：显示标签选择器 */}
                  {generationMode === 'NEW_IDEA' && (
                    renderSelectors()
                  )}

                  {/* REDESIGN_SITE 模式：显示 URL 输入框 */}
                  {generationMode === 'REDESIGN_SITE' && (
                    <div className="relative w-full max-w-xl">
                      <div className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                        <LinkIcon className="w-4 h-4" />
                      </div>
                      <input
                        ref={cloneUrlInputRef}
                        type="url"
                        placeholder={t('hero.placeholder_redesign')}
                        className="w-full pl-9 pr-4 py-2.5 rounded-xl border bg-slate-50 dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all cursor-text font-mono text-sm"
                      />
                    </div>
                  )}
                </div>

                <Textarea
                  value={requirement}
                  onChange={(e) => {
                    setRequirement(e.target.value);
                    onRequirementChange?.(e.target.value);
                  }}
                  data-testid="hero-requirement-input"
                  placeholder={
                    generationMode === 'REDESIGN_SITE' 
                      ? t('hero.placeholder_redesign')
                      : t('hero.placeholder_create')
                  }
                  className="flex-1 w-full resize-none border-none bg-transparent text-sm placeholder:text-slate-300 dark:placeholder:text-slate-600 focus-visible:ring-0 p-0 leading-relaxed font-light min-h-[120px]"
                />

                {/* Input Tools - 暂时隐藏输入模式切换按钮，只显示字符计数 */}
                <div className="flex items-center justify-end mt-4 md:mt-0 pt-4 border-t border-slate-100 dark:border-slate-800/50">
                  {/* TODO: 后续添加上传图片、语音输入、链接、上传文档功能按钮
                  <div className="flex items-center gap-1 text-slate-400">
                    <TooltipProvider>
                      {inputModes.map((modeConfig) => (
                        <Tooltip key={modeConfig.mode}>
                          <TooltipTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon"
                              className={cn(
                                "h-9 w-9 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors",
                                inputMode === modeConfig.mode && "bg-slate-100 dark:bg-slate-800 text-primary"
                              )}
                              onClick={() => setInputMode(modeConfig.mode)}
                            >
                              {modeConfig.icon}
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>
                            <p>{modeConfig.label}</p>
                          </TooltipContent>
                        </Tooltip>
                      ))}
                    </TooltipProvider>
                  </div>
                  */}

                  <span className="text-xs text-slate-300 dark:text-slate-600 font-mono">
                    {requirement.length}/500
                  </span>
                </div>
              </div>

              {/* Generate Button Wrapper - Positioned absolutely or flex-end */}
              <div className="absolute bottom-6 right-6 z-20">
                <Button
                  onClick={handleSubmitRequirement}
                  size="lg"
                  data-testid="hero-generate-button"
                  className="bg-[#0f172a] hover:bg-[#1e293b] text-white dark:bg-white dark:text-black dark:hover:bg-slate-200 rounded-xl px-6 py-2.5 h-auto text-sm font-medium shadow-lg hover:shadow-xl transition-all hover:scale-105"
                >
                  <Sparkles className="w-4 h-4 mr-2" />
                  {t('hero.btn_generate')}
                </Button>
              </div>

            </div>
          </div>

        </div>

      </div>
    </div>
  );
}

// Keep existing configs
// INPUT_MODES removed as it is now defined inside the component to support i18n
