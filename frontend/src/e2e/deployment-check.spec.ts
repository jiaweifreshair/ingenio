import { test, expect } from '@playwright/test';

test('Deployment Health Check', async ({ page, request }) => {
  // 1. Check Frontend Availability
  console.log('Checking Frontend at http://localhost:3000 ...');
  const response = await page.goto('/');
  expect(response?.status()).toBe(200);
  
  // Check Title
  await expect(page).toHaveTitle(/Ingenio|妙构/);
  console.log('Frontend is UP and Title is correct.');

  // 2. Check Backend Availability directly from the test runner
  console.log('Checking Backend at http://localhost:8080/api/actuator/health ...');
  const backendResponse = await request.get('http://localhost:8080/api/actuator/health');
  expect(backendResponse.status()).toBe(200);
  
  const healthData = await backendResponse.json();
  console.log('Backend Health:', healthData);
  expect(healthData.status).toBe('UP');
});
