/**
 * Plan Routing Types
 * Shared between backend and frontend
 */

export enum RoutingBranch {
  CLONE = 'CLONE',
  DESIGN = 'DESIGN',
  HYBRID = 'HYBRID',
}

export enum RequirementIntent {
  CLONE_EXISTING_WEBSITE = 'CLONE_EXISTING_WEBSITE',
  DESIGN_FROM_SCRATCH = 'DESIGN_FROM_SCRATCH',
  HYBRID_CLONE_AND_CUSTOMIZE = 'HYBRID_CLONE_AND_CUSTOMIZE',
}

export interface TemplateMatchResult {
  template: any; // Replace with proper Template type if available
  matchScore: number;
  matchedKeywords: string[];
}

export interface StyleVariant {
  styleId: string;
  styleName: string;
  styleCode: string;
  previewHtml?: string;
  thumbnailUrl?: string;
  colorTheme?: string;
}

export interface PlanRoutingResult {
  appSpecId: string;
  intent: RequirementIntent;
  confidence: number;
  branch: RoutingBranch;
  matchedTemplateResults?: TemplateMatchResult[];
  styleVariants?: StyleVariant[];
  prototypeGenerated: boolean;
  prototypeUrl?: string;
  selectedStyleId?: string;
  nextAction: string;
  requiresUserConfirmation: boolean;
  metadata?: Record<string, unknown>;
}

export interface DesignConfirmResult {
  success: boolean;
  message: string;
  appSpecId?: string;
  canProceedToExecute?: boolean;
}
