import ts from 'typescript';

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

export class TypeScriptCheck {
  /**
   * Validates TypeScript code syntax in-memory.
   * NOTE: This checks for syntax errors only, not semantic/type errors (which require a full compiler host).
   */
  static validate(code: string): ValidationResult {
    if (!code || code.trim() === '') {
      return { valid: false, errors: ['Code is empty'] };
    }

    try {
      const sourceFile = ts.createSourceFile(
        'temp.ts',
        code,
        ts.ScriptTarget.Latest,
        true // setParentNodes
      );

      // parseDiagnostics is an internal TS property not exposed in public types
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const diagnostics = (sourceFile as unknown as { parseDiagnostics?: ts.Diagnostic[] }).parseDiagnostics;

      if (diagnostics && diagnostics.length > 0) {
        const errors = diagnostics.map(diagnostic => {
          const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n');
          if (diagnostic.file && diagnostic.start !== undefined) {
            const { line, character } = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
            return `Line ${line + 1}, Col ${character + 1}: ${message}`;
          }
          return message;
        });

        return { valid: false, errors };
      }

      return { valid: true, errors: [] };
    } catch (e) {
      return { 
        valid: false, 
        errors: [`Internal Executor Error: ${e instanceof Error ? e.message : String(e)}`] 
      };
    }
  }
}