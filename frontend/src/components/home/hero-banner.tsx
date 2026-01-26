"use client";

import { useState, useEffect, useRef } from "react";

import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";

import { Textarea } from "@/components/ui/textarea";


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
  Wand2
} from "lucide-react";

import {

  type IndustryType,

  type AppComplexityMode,

  type AICapabilityType,

} from "@/types/smart-builder";

import { cn } from "@/lib/utils";

// TODO: 后续添加输入模式切换功能时取消注释
// import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { useLanguage } from "@/contexts/LanguageContext";

// Removed unused Select imports

// import {

//   DropdownMenu,
//   DropdownMenuCheckboxItem,
//   DropdownMenuContent,
//   // DropdownMenuItem, // Removed as it is unused
//   DropdownMenuLabel,
//   DropdownMenuSeparator,
//   DropdownMenuTrigger,
// } from "@/components/ui/dropdown-menu";



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

  onIndustryChange: _onIndustryChange,

  onModeChange: _onModeChange,

    onCapabilitiesChange: _onCapabilitiesChange,

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

    // const [localIndustry, setLocalIndustry] = useState<IndustryType | null>(selectedIndustry || null);

    // const [localMode, setLocalMode] = useState<AppComplexityMode | null>(externalSelectedMode || null);

    // const [localCapabilities, setLocalCapabilities] = useState<AICapabilityType[]>(externalSelectedCapabilities || []);

  

    // Update local state when props change

    useEffect(() => {

      // if (selectedIndustry !== undefined) setLocalIndustry(selectedIndustry);

      // if (externalSelectedMode !== undefined) setLocalMode(externalSelectedMode);

      // if (externalSelectedCapabilities !== undefined) setLocalCapabilities(externalSelectedCapabilities);

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

  

      // 1. Tags Context (Simplified: Don't inject tags here, rely on Wizard to ask or infer)
      // We removed the selectors, so we just pass the requirement.
      // If we want to keep them as hidden state or default, we can, but for now let's keep it simple.
  
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

          toast({ title: "请输入网址", description: "重构模式需要输入目标网站的 URL", variant: "destructive" });

          return;

        }

      } else {

        if (!currentPrompt.trim()) {

          toast({ title: "请输入需求", description: "请描述您想要构建的应用", variant: "destructive" });

          return;

        }

      }




      // Call parent handler to start wizard

      if (onLaunchWizard) {

        onLaunchWizard(currentPrompt, {

          // industry: localIndustry,

          // mode: localMode,

          // capabilities: localCapabilities
          industry: null,
          mode: null,
          capabilities: []

        });

      } else {

        // Fallback: Legacy Redirect (should not happen in new flow)

        // V2 Integration: Redirect to /create-v2

        setWizardStep('REDIRECTING');

        

        // Construct query parameters

        const params = new URLSearchParams();

        params.set('q', fullPrompt);

        
        // if (localIndustry) params.set('industry', localIndustry);

        // if (localMode) params.set('mode', localMode);

        

              // Use router to push

        

              router.push(`/?${params.toString()}`);

      }

    };



  /**

   * 切换本地 AI 能力选择，并同步到父组件

   */

  /*
  const toggleLocalCapability = (capId: AICapabilityType) => {
    setLocalCapabilities(prev => {
      const exists = prev.includes(capId);
      return exists ? prev.filter(c => c !== capId) : [...prev, capId];
    });
  };
  */

  // 使用 useEffect 同步 capabilities 变化到父组件，避免渲染期间 setState
  // 只有当本地状态与外部状态不同时才同步，避免无限循环
  // useEffect(() => {
  //   const externalStr = JSON.stringify(externalSelectedCapabilities || []);
  //   const localStr = JSON.stringify(localCapabilities);
  //   if (localStr !== externalStr) {
  //     onCapabilitiesChange?.(localCapabilities);
  //   }
  // }, [localCapabilities, externalSelectedCapabilities, onCapabilitiesChange]);



  /**
   * 阻止 DropdownMenuTrigger 在点击清除按钮时误触发
   */
  // const stopDropdownTrigger = (e: React.PointerEvent<HTMLButtonElement>) => {
  //   e.preventDefault();
  //   e.stopPropagation();
  // };


  /**
   * 随机创意生成器
   */
  const handleSurpriseMe = () => {
    const ideas = [
        "在这个快速发展的时代，打造一款能够一键生成创意海报的工具。",
        "为城市忙碌的白领，设计一个极简的冥想与放松应用。",
        "创建一个基于地理位置的社区旧物交换平台。",
        "开发一个能够智能规划周末亲子游路线的助手。",
        "构建一个帮助独立开发者管理订阅收入的仪表盘。"
    ];
    const randomIdea = ideas[Math.floor(Math.random() * ideas.length)];
    setRequirement(randomIdea);
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
          <p className="text-lg md:text-xl text-purple-600 dark:text-purple-400 mb-10 max-w-2xl mx-auto leading-relaxed font-medium">
            Vibe Coding · 融入 AI 引擎
          </p>
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
              <Wand2 className="w-4 h-4 text-purple-500" /> 重构旧站
            </button>
          </div>

          {/* 2. Input Canvas */}
          <div className="relative w-full bg-white dark:bg-slate-900 rounded-[2rem] p-2 shadow-2xl border border-white/20 ring-1 ring-black/5 dark:ring-white/10">
            <div className="relative min-h-[220px] flex flex-col">

              <div className="flex-1 p-6 flex flex-col animate-in fade-in zoom-in-95">
                
                {/* 模式特定的顶部控件 */}
                <div className="mb-4 space-y-4">
                  
                  {/* NEW_IDEA 模式：显示创意生成按钮 */}
                  {generationMode === 'NEW_IDEA' && (
                     <Button
                        variant="outline"
                        size="sm"
                        onClick={handleSurpriseMe}
                        className="rounded-full text-xs font-medium border-slate-200 dark:border-slate-800 text-slate-500 hover:text-purple-600 hover:border-purple-200 hover:bg-purple-50 dark:hover:bg-purple-900/20 dark:text-slate-400 transition-all"
                     >
                       <Sparkles className="w-3.5 h-3.5 mr-1.5 text-purple-500" />
                       随机创意
                     </Button>
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
                        placeholder="输入现有网站 URL 进行重构..."
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
                    {/* Placeholder for future tools */}

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
