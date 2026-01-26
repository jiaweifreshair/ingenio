'use client';

import { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import { getProjectById, regenerateProject } from '@/lib/api/projects';
import { getAppSpecById } from '@/lib/api/appspecs';
import { ArrowLeft, Loader2, RefreshCw } from 'lucide-react';
import Link from 'next/link';

export default function EditProjectPage() {
  const router = useRouter();
  const params = useParams();
  const { toast } = useToast();
  const projectId = params?.id as string;

  const [isLoading, setIsLoading] = useState(true);
  const [isRegenerating, setIsRegenerating] = useState(false);
  const [requirement, setRequirement] = useState('');
  const [projectName, setProjectName] = useState('');

  useEffect(() => {
    async function loadData() {
      try {
        // 1. Load Project
        const project = await getProjectById(projectId);
        setProjectName(project.name);

        // 2. Load AppSpec to get current requirement
        if (project.appSpecId) {
          try {
            const appSpec = await getAppSpecById(project.appSpecId);
            const userReq = appSpec.specContent?.userRequirement;
            if (typeof userReq === 'string') {
              setRequirement(userReq);
            }
          } catch (err) {
            console.error('Failed to load AppSpec:', err);
            // Non-blocking, user can input fresh requirement if fetch fails
          }
        }
      } catch (error) {
        console.error('Failed to load project:', error);
        toast({
          variant: 'destructive',
          title: 'Error',
          description: 'Failed to load project data',
        });
      } finally {
        setIsLoading(false);
      }
    }

    if (projectId) {
      loadData();
    }
  }, [projectId, toast]);

  const handleRegenerate = async () => {
    if (!requirement.trim()) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'Requirement cannot be empty',
      });
      return;
    }

    setIsRegenerating(true);
    try {
      const result = await regenerateProject(projectId, undefined, undefined, requirement);
      
      toast({
        title: 'Success',
        description: 'New version generation started successfully!',
      });
      
      // Navigate to Wizard to view progress (using new AppSpec ID)
      if (result.newVersionId) {
        router.push(`/wizard/${result.newVersionId}`);
      } else {
        router.push('/dashboard');
      }
    } catch (error) {
      console.error('Regeneration failed:', error);
      toast({
        variant: 'destructive',
        title: 'Regeneration Failed',
        description: error instanceof Error ? error.message : 'Unknown error',
      });
    } finally {
      setIsRegenerating(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="container max-w-2xl py-10">
      <div className="mb-6">
        <Link 
          href={`/projects/${projectId}`}
          className="flex items-center text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back to Project
        </Link>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Edit & Regenerate</CardTitle>
          <CardDescription>
            Update requirements for <strong>{projectName}</strong> and generate a new version.
            This will create a new version in the history.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            <div className="space-y-2">
              <label htmlFor="requirement" className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                Data Requirement / Prompt
              </label>
              <Textarea
                id="requirement"
                placeholder="Describe your application requirements..."
                className="min-h-[200px] resize-y font-mono text-sm"
                value={requirement}
                onChange={(e) => setRequirement(e.target.value)}
              />
              <p className="text-xs text-muted-foreground">
                Modify your original prompt to refine the generated application.
              </p>
            </div>
          </div>
        </CardContent>
        <CardFooter className="flex justify-between border-t pt-6">
          <Button variant="outline" onClick={() => router.back()}>
            Cancel
          </Button>
          <Button onClick={handleRegenerate} disabled={isRegenerating || !requirement.trim()}>
            {isRegenerating ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Starting Generation...
              </>
            ) : (
              <>
                <RefreshCw className="mr-2 h-4 w-4" />
                Regenerate New Version
              </>
            )}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
