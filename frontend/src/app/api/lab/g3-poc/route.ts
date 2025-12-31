import { NextRequest, NextResponse } from 'next/server';
import { runG3Loop } from '@/lib/lab/g3-engine';

export const runtime = 'nodejs'; // Use Node.js runtime for 'typescript' package compatibility

export async function POST(req: NextRequest) {
  const { requirement } = await req.json();

  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    async start(controller) {
      try {
        const generator = runG3Loop(requirement || 'Implement Fibonacci');
        
        for await (const event of generator) {
          // SSE format: data: <json>\n\n
          const data = JSON.stringify(event);
          controller.enqueue(encoder.encode(`data: ${data}\n\n`));
        }
      } catch (error) {
        console.error('G3 Stream Error:', error);
        const errorEvent = JSON.stringify({ 
          type: 'LOG', 
          data: { 
            role: 'EXECUTOR', 
            level: 'error', 
            message: `Internal Server Error: ${error instanceof Error ? error.message : String(error)}`,
            timestamp: new Date().toISOString()
          } 
        });
        controller.enqueue(encoder.encode(`data: ${errorEvent}\n\n`));
      } finally {
        controller.close();
      }
    },
  });

  return new NextResponse(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  });
}