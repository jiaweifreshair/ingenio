import { G3LogEntry, G3Artifact } from '@/types/g3';
import { TypeScriptCheck } from './typescript-check';
import { PlayerAgent, CoachAgent } from './mock-agents';

export type G3Event =
  | { type: 'LOG', data: G3LogEntry }
  | { type: 'ARTIFACT', data: G3Artifact };

export async function* runG3Loop(requirement: string): AsyncGenerator<G3Event, void, unknown> {
  const MAX_RETRIES = 3;
  let currentCode = '';
  
  // --- Phase 1: Player Generation ---
  yield { 
    type: 'LOG', 
    data: { timestamp: new Date().toISOString(), role: 'PLAYER', level: 'info', message: `Received requirement: "${requirement}"` } 
  };
  
  yield { 
    type: 'LOG', 
    data: { timestamp: new Date().toISOString(), role: 'PLAYER', level: 'info', message: 'Generating initial solution code...' } 
  };

  const initialArtifact = await PlayerAgent.generate(requirement);
  currentCode = initialArtifact.code;
  
  yield { 
    type: 'LOG', 
    data: { timestamp: new Date().toISOString(), role: 'PLAYER', level: 'success', message: 'Initial code generated.' } 
  };
  
  yield {
    type: 'ARTIFACT',
    data: { ...initialArtifact, isValid: false } // Status TBD
  };

  // --- Phase 2: The Loop (Executor <-> Coach) ---
  for (let round = 1; round <= MAX_RETRIES; round++) {
    yield { 
      type: 'LOG', 
      data: { timestamp: new Date().toISOString(), role: 'EXECUTOR', level: 'info', message: `[Round ${round}/${MAX_RETRIES}] Compiling and checking syntax...` } 
    };

    const validation = TypeScriptCheck.validate(currentCode);

    if (validation.valid) {
      // Success!
      yield { 
        type: 'LOG', 
        data: { timestamp: new Date().toISOString(), role: 'EXECUTOR', level: 'success', message: 'Syntax Check Passed! ✅' } 
      };
      yield {
        type: 'ARTIFACT',
        data: { code: currentCode, filename: 'fibonacci.ts', language: 'typescript', isValid: true }
      };
      return; // Exit loop
    }

    // Failure - Report errors
    yield { 
      type: 'LOG', 
      data: { timestamp: new Date().toISOString(), role: 'EXECUTOR', level: 'error', message: `Syntax Errors Found: ${validation.errors.length}` } 
    };
    
    for (const err of validation.errors) {
      yield { 
        type: 'LOG', 
        data: { timestamp: new Date().toISOString(), role: 'EXECUTOR', level: 'warn', message: ` >> ${err}` } 
      };
    }

    if (round < MAX_RETRIES) {
      // Call Coach to fix
      yield { 
        type: 'LOG', 
        data: { timestamp: new Date().toISOString(), role: 'COACH', level: 'info', message: 'Analyzing errors and patching code...' } 
      };

      const fixedArtifact = await CoachAgent.fix(currentCode, validation.errors);
      currentCode = fixedArtifact.code;

      yield { 
        type: 'LOG', 
        data: { timestamp: new Date().toISOString(), role: 'COACH', level: 'success', message: 'Code patched. Submitting for re-evaluation.' } 
      };
      
      yield {
        type: 'ARTIFACT',
        data: { ...fixedArtifact, isValid: false } // Re-evaluate next loop
      };
    } else {
      yield { 
        type: 'LOG', 
        data: { timestamp: new Date().toISOString(), role: 'EXECUTOR', level: 'error', message: 'Max retries reached. Validation failed.' } 
      };
    }
  }
}