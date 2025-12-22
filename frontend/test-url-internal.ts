
import { getApiBaseUrl, buildApiUrl } from './src/lib/api/base-url';

console.log("Testing API URL generation...");

try {
  // 模拟 Next.js 环境变量 (如果运行时有的话)
  // process.env.NEXT_PUBLIC_API_BASE_URL = ...; 

  const baseUrl = getApiBaseUrl();
  console.log(`Base URL: '${baseUrl}'`);
  
  const endpoint = '/v1/test';
  const fullUrl = buildApiUrl(endpoint);
  console.log(`Full URL: '${fullUrl}'`);
  
  // 尝试模拟 new URL
  console.log("Attempting new URL(fullUrl)...");
  const u = new URL(fullUrl);
  console.log("new URL(fullUrl) success:", u.href);

} catch (e) {
  console.error("Error:", e);
}
