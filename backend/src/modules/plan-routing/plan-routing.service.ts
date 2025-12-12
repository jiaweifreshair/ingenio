import { Injectable, Logger, Inject } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { v4 as uuidv4 } from 'uuid';
import { IModelProvider } from '@common/interfaces/model-provider.interface';
import { AppSpecEntity } from '../../entities/app-spec.entity';
import {
  PlanRoutingResult,
  RoutingBranch,
  RequirementIntent,
  TemplateMatchResult,
  StyleVariant,
  DesignConfirmResult,
} from '@shared/types/plan-routing.types';

@Injectable()
export class PlanRoutingService {
  private readonly logger = new Logger(PlanRoutingService.name);

  constructor(
    @InjectRepository(AppSpecEntity)
    private readonly appSpecRepo: Repository<AppSpecEntity>,
    @Inject('IModelProvider')
    private readonly modelProvider: IModelProvider,
  ) {}

  async routeRequirement(
    requirement: string,
    userId: string,
    tenantId: string,
  ): Promise<PlanRoutingResult> {
    this.logger.log(`Routing requirement for user ${userId}: ${requirement.substring(0, 50)}...`);

    // 1. Intent Analysis using LLM
    const intentAnalysis = await this.analyzeIntentWithLLM(requirement);
    
    // 2. Template Matching (Mock for now, or simple keyword match)
    const matchedTemplates = this.matchTemplates(requirement, intentAnalysis.intent);

    // 3. Create AppSpec in 'planning' state
    const appSpecId = uuidv4();
    const appSpec = this.appSpecRepo.create({
      id: appSpecId,
      tenantId,
      userId,
      name: `Project-${new Date().toISOString().split('T')[0]}`,
      description: requirement,
      status: 'draft',
      requirementText: requirement,
      metadata: {
        intent: intentAnalysis.intent,
        branch: intentAnalysis.branch,
        confidence: intentAnalysis.confidence,
        matchedKeywords: intentAnalysis.keywords,
        stage: 'planning',
      },
    });

    await this.appSpecRepo.save(appSpec);

    // 4. Construct Result
    const result: PlanRoutingResult = {
      appSpecId,
      intent: intentAnalysis.intent,
      confidence: intentAnalysis.confidence,
      branch: intentAnalysis.branch,
      matchedTemplateResults: matchedTemplates,
      styleVariants: intentAnalysis.branch === RoutingBranch.DESIGN ? this.getStyleVariants() : [],
      prototypeGenerated: false,
      nextAction: intentAnalysis.branch === RoutingBranch.CLONE ? 'Please select a template' : 'Please select a style',
      requiresUserConfirmation: true,
    };

    return result;
  }

  async selectStyle(appSpecId: string, styleId: string): Promise<PlanRoutingResult> {
    this.logger.log(`Selecting style ${styleId} for AppSpec ${appSpecId}`);

    const appSpec = await this.appSpecRepo.findOne({ where: { id: appSpecId } });
    if (!appSpec) {
      throw new Error('AppSpec not found');
    }

    const metadata: Record<string, any> = {
      ...(appSpec.metadata ?? {}),
      selectedStyleId: styleId,
    };
    appSpec.metadata = metadata;

    await this.appSpecRepo.save(appSpec);

    // Generate "Preview" (Mock for now - just return a success indicator)
    const previewUrl = `https://preview.ingenio.ai/${appSpecId}?style=${styleId}`;

    const intent = (metadata.intent ?? RequirementIntent.DESIGN_FROM_SCRATCH) as RequirementIntent;
    const confidence = (metadata.confidence ?? 0.8) as number;
    const branch = (metadata.branch ?? RoutingBranch.DESIGN) as RoutingBranch;

    return {
      appSpecId,
      intent,
      confidence,
      branch,
      prototypeGenerated: true,
      prototypeUrl: previewUrl,
      selectedStyleId: styleId,
      nextAction: 'Please confirm the design',
      requiresUserConfirmation: true,
    };
  }

  async confirmDesign(appSpecId: string): Promise<DesignConfirmResult> {
    this.logger.log(`Confirming design for AppSpec ${appSpecId}`);

    const appSpec = await this.appSpecRepo.findOne({ where: { id: appSpecId } });
    if (!appSpec) {
      throw new Error('AppSpec not found');
    }

    const metadata = appSpec.metadata ?? {};
    const styleId = metadata.selectedStyleId as string | undefined;
    const intent = metadata.intent as RequirementIntent | undefined;
    
    let refinedRequirement = appSpec.requirementText ?? '';
    if (styleId) {
      refinedRequirement += `\n\nPlease use the design style: ${styleId}.`;
    }
    if (intent) {
       refinedRequirement += `\n\nIntent: ${intent}.`;
    }

    // Trigger generation (async in real world, here just acknowledge)
    // In a real implementation, we would call workersClient.invokeExecuteAgent(..., { plan: ... })
    
    return {
      success: true,
      message: 'Design confirmed. Starting generation...',
      appSpecId,
      canProceedToExecute: true,
    };
  }

  async getHistory(userId: string, tenantId: string): Promise<AppSpecEntity[]> {
    return this.appSpecRepo.find({
      where: { userId, tenantId },
      order: { createdAt: 'DESC' },
      take: 20,
    });
  }

  // Private Helpers

  private async analyzeIntentWithLLM(requirement: string): Promise<{
    intent: RequirementIntent;
    branch: RoutingBranch;
    confidence: number;
    keywords: string[];
  }> {
    const prompt = `
      Analyze the following user requirement for a software application:
      "${requirement}"
      
      Classify the intent into one of:
      1. CLONE_EXISTING_WEBSITE (User wants to copy/clone a specific site)
      2. DESIGN_FROM_SCRATCH (User wants a new original design)
      3. HYBRID_CLONE_AND_CUSTOMIZE (User mentions a reference site but wants major changes)
      
      Return ONLY a JSON object with:
      {
        "intent": "CLONE_EXISTING_WEBSITE" | "DESIGN_FROM_SCRATCH" | "HYBRID_CLONE_AND_CUSTOMIZE",
        "confidence": number (0.0 to 1.0),
        "keywords": ["string", "string"]
      }
    `;

    try {
      const response = await this.modelProvider.chat([
        { role: 'system', content: 'You are an expert software architect assistant.' },
        { role: 'user', content: prompt }
      ]);

      const content = response.content;
      const jsonMatch = content.match(/\{[\s\S]*\}/);
      if (jsonMatch) {
        const result = JSON.parse(jsonMatch[0]);
        const branch = result.intent === 'DESIGN_FROM_SCRATCH' ? RoutingBranch.DESIGN : 
                       result.intent === 'CLONE_EXISTING_WEBSITE' ? RoutingBranch.CLONE : RoutingBranch.HYBRID;
        return {
          intent: result.intent,
          branch,
          confidence: result.confidence || 0.9,
          keywords: result.keywords || [],
        };
      }
    } catch (e) {
      this.logger.error('LLM Analysis failed', e);
    }

    // Fallback
    const reqLower = requirement.toLowerCase();
    if (reqLower.includes('clone') || reqLower.includes('copy') || reqLower.includes('like')) {
       return { intent: RequirementIntent.CLONE_EXISTING_WEBSITE, branch: RoutingBranch.CLONE, confidence: 0.8, keywords: [] };
    }
    return { intent: RequirementIntent.DESIGN_FROM_SCRATCH, branch: RoutingBranch.DESIGN, confidence: 0.8, keywords: [] };
  }

  private matchTemplates(_requirement: string, intent: RequirementIntent): TemplateMatchResult[] {
    if (intent === RequirementIntent.DESIGN_FROM_SCRATCH) return [];

    // Mock templates
    return [
      {
        template: {
          id: 'tpl_edu_01',
          name: 'Online Education Platform',
          description: 'Complete platform with courses and video.',
          category: 'Education',
          thumbnailUrl: 'https://placehold.co/600x400/e0e7ff/4f46e5?text=Education',
        },
        matchScore: 0.95,
        matchedKeywords: ['education', 'course'],
      },
       {
        template: {
          id: 'tpl_shop_01',
          name: 'E-commerce Store',
          description: 'Modern shop with cart and payment.',
          category: 'E-commerce',
          thumbnailUrl: 'https://placehold.co/600x400/fce7f3/db2777?text=Shop',
        },
        matchScore: 0.85,
        matchedKeywords: ['shop', 'buy'],
      }
    ];
  }

  private getStyleVariants(): StyleVariant[] {
    return [
      { styleId: 'A', styleName: 'Modern Minimal', styleCode: 'modern_minimal', colorTheme: '#000000', thumbnailUrl: 'https://placehold.co/300x200/black/white?text=Modern+Minimal' },
      { styleId: 'B', styleName: 'Tech Futuristic', styleCode: 'tech_futuristic', colorTheme: '#0ea5e9', thumbnailUrl: 'https://placehold.co/300x200/0f172a/0ea5e9?text=Tech+Future' },
      { styleId: 'C', styleName: 'Warm Healing', styleCode: 'warm_healing', colorTheme: '#f59e0b', thumbnailUrl: 'https://placehold.co/300x200/fffbeb/f59e0b?text=Warm+Healing' },
      { styleId: 'D', styleName: 'Geek Hacker', styleCode: 'geek_hacker', colorTheme: '#22c55e', thumbnailUrl: 'https://placehold.co/300x200/052e16/22c55e?text=Geek+Hacker' },
      { styleId: 'E', styleName: 'Business Pro', styleCode: 'business_pro', colorTheme: '#1e293b', thumbnailUrl: 'https://placehold.co/300x200/f8fafc/1e293b?text=Business+Pro' },
      { styleId: 'F', styleName: 'Vibrant Youth', styleCode: 'vibrant_youth', colorTheme: '#f43f5e', thumbnailUrl: 'https://placehold.co/300x200/fff1f2/f43f5e?text=Vibrant+Youth' },
      { styleId: 'G', styleName: 'Nature Eco', styleCode: 'nature_eco', colorTheme: '#10b981', thumbnailUrl: 'https://placehold.co/300x200/ecfdf5/10b981?text=Nature+Eco' },
    ];
  }
}
