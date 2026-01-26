/**
 * 健康检查接口（E2E/运维用）
 *
 * 用途：
 * - 给 Playwright 的 webServer 探测使用，避免首页 SSR/外部依赖导致的“服务未就绪”误判
 * - 为本地排障提供一个稳定、无副作用的可达路径
 *
 * 说明：
 * - 该接口不依赖后端服务，不读取用户态信息
 */

import { NextResponse } from 'next/server';

export function GET() {
  return NextResponse.json({ status: 'ok' });
}

