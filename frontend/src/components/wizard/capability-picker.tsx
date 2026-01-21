import React, { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Bot, Link as LinkIcon, Server } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';

export interface CapabilityPickerProps {
  requirement: string;
  onCapabilityConfigChange: (config: CapabilityConfig) => void;
  className?: string;
}

/**
 * 智能体能力配置
 *
 * 用途：把“是否启用智能体 + 连接参数”结构化回传给 Wizard 状态机。
 */
export interface CapabilityConfig {
  code: "ai_agent_connect";
  enabled: boolean;
  config: {
    agentType: "OPENAI" | "AGENT_SCOPE" | "EXTERNAL_URL";
    baseUrl: string;
    apiKey: string;
  };
}

export const CapabilityPicker: React.FC<CapabilityPickerProps> = ({
  requirement,
  onCapabilityConfigChange,
  className
}) => {
  const [showAgentConfig, setShowAgentConfig] = useState(false);
  const [agentType, setAgentType] = useState<CapabilityConfig["config"]["agentType"]>("OPENAI");
  const [baseUrl, setBaseUrl] = useState('');
  const [apiKey, setApiKey] = useState('');

  // Simple keyword detection for Agent requirements
  useEffect(() => {
    const keywords = ['agent', 'bot', 'assistant', 'evaluation', 'review', 'analysis', '智能体', '评估'];
    const hasAgentKeyword = keywords.some(kw => requirement.toLowerCase().includes(kw));
    
    if (hasAgentKeyword && !showAgentConfig) {
      setShowAgentConfig(true);
    }
  }, [requirement]);

  // Update config when local state changes
  useEffect(() => {
    if (showAgentConfig) {
      onCapabilityConfigChange({
        code: 'ai_agent_connect',
        enabled: true,
        config: {
          agentType,
          baseUrl,
          apiKey
        }
      });
    }
  }, [showAgentConfig, agentType, baseUrl, apiKey, onCapabilityConfigChange]);

  if (!showAgentConfig) {
    return null;
  }

  return (
    <Card className={className}>
      <CardHeader className="pb-3">
        <CardTitle className="text-base flex items-center gap-2">
          <Bot className="h-5 w-5 text-primary" />
          智能体集成 (AI Agent)
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <Alert className="bg-primary/5 border-primary/20">
          <Bot className="h-4 w-4" />
          <AlertTitle>检测到智能体需求</AlertTitle>
          <AlertDescription>
            您的应用似乎需要 AI 智能体能力。请配置智能体服务：
          </AlertDescription>
        </Alert>

        <div className="space-y-3">
          <Label>对接方式</Label>
          <RadioGroup
            value={agentType}
            onValueChange={(value) => setAgentType(value as CapabilityConfig["config"]["agentType"])}
            className="grid grid-cols-3 gap-2"
          >
            <div>
              <RadioGroupItem value="OPENAI" id="type-openai" className="peer sr-only" />
              <Label
                htmlFor="type-openai"
                className="flex flex-col items-center justify-between rounded-md border-2 border-muted bg-popover p-2 hover:bg-accent hover:text-accent-foreground peer-data-[state=checked]:border-primary [&:has([data-state=checked])]:border-primary"
              >
                <Bot className="mb-2 h-4 w-4" />
                <span className="text-xs">OpenAI兼容</span>
              </Label>
            </div>
            <div>
              <RadioGroupItem value="AGENT_SCOPE" id="type-agentscope" className="peer sr-only" />
              <Label
                htmlFor="type-agentscope"
                className="flex flex-col items-center justify-between rounded-md border-2 border-muted bg-popover p-2 hover:bg-accent hover:text-accent-foreground peer-data-[state=checked]:border-primary [&:has([data-state=checked])]:border-primary"
              >
                <Server className="mb-2 h-4 w-4" />
                <span className="text-xs">AgentScope</span>
              </Label>
            </div>
            <div>
              <RadioGroupItem value="EXTERNAL_URL" id="type-external" className="peer sr-only" />
              <Label
                htmlFor="type-external"
                className="flex flex-col items-center justify-between rounded-md border-2 border-muted bg-popover p-2 hover:bg-accent hover:text-accent-foreground peer-data-[state=checked]:border-primary [&:has([data-state=checked])]:border-primary"
              >
                <LinkIcon className="mb-2 h-4 w-4" />
                <span className="text-xs">外部URL</span>
              </Label>
            </div>
          </RadioGroup>
        </div>

        <div className="space-y-2">
          <Label htmlFor="base-url">服务地址 (Base URL)</Label>
          <Input 
            id="base-url" 
            placeholder={agentType === 'OPENAI' ? "https://api.openai.com" : "http://localhost:8080"}
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="api-key">API Key (可选)</Label>
          <Input 
            id="api-key" 
            type="password"
            placeholder="sk-..."
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
          />
        </div>
      </CardContent>
    </Card>
  );
};
