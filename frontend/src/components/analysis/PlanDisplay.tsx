'use client';

import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Loader2, MessageSquare, CheckCircle2, Play } from 'lucide-react';

interface PlanDisplayProps {
  planContent: string;
  onConfirm: () => void;
  onModify: (newRequirement: string) => void;
  isGenerating?: boolean;
}

export function PlanDisplay({ planContent, onConfirm, onModify, isGenerating = false }: PlanDisplayProps) {
  const [modification, setModification] = useState('');
  const [isModifying, setIsModifying] = useState(false);

  const handleModifySubmit = () => {
    if (!modification.trim()) return;
    setIsModifying(true);
    onModify(modification);
    // Note: Parent should handle the re-generation or update logic
    setModification('');
    setIsModifying(false); // Reset after submit (or keep loading if async)
  };

  return (
    <div className="flex flex-col h-full space-y-4 animate-in fade-in duration-500">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent flex items-center gap-2">
          <CheckCircle2 className="w-6 h-6 text-green-500" />
          Technical Blueprint
        </h2>
        <span className="text-sm text-muted-foreground px-3 py-1 bg-secondary rounded-full">
          Step 6/6 Completed
        </span>
      </div>

      <div className="flex-1 min-h-0 border rounded-xl bg-card/50 backdrop-blur-sm overflow-hidden flex flex-col">
        <ScrollArea className="flex-1 p-6">
          <div className="prose prose-sm dark:prose-invert max-w-none">
            <ReactMarkdown
              components={{
                code({ inline, className, children, ...props }: { inline?: boolean; className?: string; children?: React.ReactNode }) {
                  const match = /language-(\w+)/.exec(className || '');
                  return !inline && match ? (
                    <SyntaxHighlighter
                      style={vscDarkPlus}
                      language={match[1]}
                      PreTag="div"
                      {...props}
                    >
                      {String(children).replace(/\n$/, '')}
                    </SyntaxHighlighter>
                  ) : (
                    <code className={className} {...props}>
                      {children}
                    </code>
                  );
                },
              }}
            >
              {planContent}
            </ReactMarkdown>
          </div>
        </ScrollArea>

        {/* Action Footer */}
        <div className="p-4 border-t bg-background/50 backdrop-blur-md space-y-4">
          
          {/* Chat/Modify Input */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <MessageSquare className="w-4 h-4" />
              <span>Adjust the plan?</span>
            </div>
            <div className="flex gap-2">
              <Textarea 
                placeholder="E.g., Change the database to MongoDB, or add a dark mode toggle..."
                value={modification}
                onChange={(e) => setModification(e.target.value)}
                className="min-h-[60px] resize-none"
              />
              <Button 
                variant="outline" 
                size="icon" 
                className="h-[60px] w-[60px]"
                onClick={handleModifySubmit}
                disabled={!modification.trim() || isGenerating}
              >
                {isModifying ? <Loader2 className="w-5 h-5 animate-spin" /> : <MessageSquare className="w-5 h-5" />}
              </Button>
            </div>
          </div>

          <div className="flex justify-end pt-2">
            <Button 
              size="lg" 
              className="w-full sm:w-auto bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white shadow-lg hover:shadow-xl transition-all duration-300"
              onClick={onConfirm}
              disabled={isGenerating}
            >
              {isGenerating ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  Generating Code...
                </>
              ) : (
                <>
                  <Play className="w-4 h-4 mr-2" />
                  Confirm & Generate Prototype
                </>
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
