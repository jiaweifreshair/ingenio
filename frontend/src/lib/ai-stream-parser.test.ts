import { describe, it, expect } from 'vitest';
import { parseFilesFromResponse, getFileType, mergeGeneratedFiles } from './ai-stream-parser';

describe('AI Stream Parser', () => {
  describe('getFileType', () => {
    it('should correctly identify file types', () => {
      expect(getFileType('test.js')).toBe('javascript');
      expect(getFileType('component.tsx')).toBe('typescript');
      expect(getFileType('styles.css')).toBe('css');
      expect(getFileType('unknown.xyz')).toBe('text');
    });
  });

  describe('parseFilesFromResponse', () => {
    it('should parse <file> tags', () => {
      const input = `
<file path="src/App.tsx">
import React from 'react';
export default function App() { return <div>Hello</div>; }
</file>
      `;
      const { files, currentFile } = parseFilesFromResponse(input);
      expect(files).toHaveLength(1);
      expect(files[0].path).toBe('src/App.tsx');
      expect(files[0].type).toBe('typescript');
      expect(files[0].content).toContain('Hello');
      expect(currentFile).toBeNull();
    });

    it('should parse incomplete <file> tags as currentFile', () => {
      const input = `
<file path="src/main.ts">
console.log('Done');
</file>
<file path="src/utils.ts">
export function add(a, b) {
      `;
      const { files, currentFile } = parseFilesFromResponse(input);
      expect(files).toHaveLength(1);
      expect(files[0].path).toBe('src/main.ts');
      
      expect(currentFile).not.toBeNull();
      expect(currentFile?.path).toBe('src/utils.ts');
      expect(currentFile?.content).toContain('export function add');
      expect(currentFile?.completed).toBe(false);
    });

    it('should parse markdown code blocks', () => {
      const input = `
Here is the code:
      																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																										<file path="src/Button.tsx">
export const Button = () => <button>Click me</button>;
</file>
      `;
      const { files, currentFile } = parseFilesFromResponse(input);
      expect(files).toHaveLength(1);
      expect(files[0].path).toBe('src/Button.tsx');
      expect(files[0].content).toContain('Click me');
      expect(currentFile).toBeNull();
    });

    it('should handle multiple files updates', () => {
      const input = `
<file path="test.txt">v1</file>
<file path="test.txt">v2</file>
      `;
      const { files } = parseFilesFromResponse(input);
      expect(files).toHaveLength(1);
      expect(files[0].content).toBe('v2');
    });

    it('should not merge when a <file> block is restarted before it is closed', () => {
      const input = `
<file path="src/components/MoodSelector.jsx">
// PARTIAL (truncated)
export default function MoodSelector() { return null }
<file path="src/components/MoodSelector.jsx">
// FINAL (regenerated)
export default function MoodSelector() { return <div>OK</div> }
</file>
      `;
      const { files, currentFile } = parseFilesFromResponse(input);
      expect(currentFile).toBeNull();
      expect(files).toHaveLength(1);
      expect(files[0].path).toBe('src/components/MoodSelector.jsx');
      expect(files[0].content).toContain('FINAL');
      expect(files[0].content).toContain('<div>OK</div>');
      expect(files[0].content).not.toContain('PARTIAL');
      expect(files[0].content).not.toContain('<file path=');
    });
  });

  describe('mergeGeneratedFiles', () => {
    it('should keep existing files when patch only returns subset', () => {
      const previous = [
        { path: 'src/App.tsx', content: 'v1', type: 'typescript', completed: true },
        { path: 'src/main.tsx', content: 'main', type: 'typescript', completed: true },
      ];
      const patch = [
        { path: 'src/App.tsx', content: 'v2', type: 'typescript', completed: true },
      ];

      const merged = mergeGeneratedFiles(previous, patch);

      expect(merged).toHaveLength(2);
      expect(merged.find(file => file.path === 'src/App.tsx')?.content).toBe('v2');
      expect(merged.find(file => file.path === 'src/main.tsx')?.content).toBe('main');
    });

    it('should append new files after existing order', () => {
      const previous = [
        { path: 'src/App.tsx', content: 'v1', type: 'typescript', completed: true },
      ];
      const patch = [
        { path: 'src/utils.ts', content: 'utils', type: 'typescript', completed: true },
      ];

      const merged = mergeGeneratedFiles(previous, patch);

      expect(merged).toHaveLength(2);
      expect(merged[0].path).toBe('src/App.tsx');
      expect(merged[1].path).toBe('src/utils.ts');
    });
  });
});
