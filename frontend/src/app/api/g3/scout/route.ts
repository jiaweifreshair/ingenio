import { NextRequest, NextResponse } from 'next/server';

const G3_ENGINE_URL = 'http://127.0.0.1:8000';

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    
    // Forward to Python G3 Engine
    const response = await fetch(`${G3_ENGINE_URL}/api/v1/g3/scout`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        ...body,
        task_type: 'SCOUT'
      }),
    });

    if (!response.ok) {
      throw new Error(`G3 Engine Error: ${response.statusText}`);
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    console.error('Scout Proxy Error:', error);
    return NextResponse.json(
      { error: error instanceof Error ? error.message : 'Unknown error' },
      { status: 500 }
    );
  }
}
