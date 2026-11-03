// Copyright (c) 2026 Kingsrook, LLC
// SPDX-License-Identifier: Apache-2.0
import { randomBytes } from 'node:crypto';
import { defineConfig } from '@playwright/test';

process.env.MOBILE_E2E_PORT ??= '57933';
process.env.MOBILE_E2E_SCANNER_PASSWORD ??= randomBytes(24).toString('base64url');
process.env.MOBILE_E2E_ADMIN_PASSWORD ??= randomBytes(24).toString('base64url');
process.env.MOBILE_DEMO_SCANNER_PASSWORD = process.env.MOBILE_E2E_SCANNER_PASSWORD;
process.env.MOBILE_DEMO_ADMIN_PASSWORD = process.env.MOBILE_E2E_ADMIN_PASSWORD;
const baseURL = `http://127.0.0.1:${process.env.MOBILE_E2E_PORT}`;

export default defineConfig({
   testDir: './tests',
   fullyParallel: false,
   workers: 1,
   retries: 0,
   timeout: 90_000,
   expect: { timeout: 15_000 },
   reporter: [['list'], ['html', { open: 'never' }]],
   globalSetup: './global-setup.mjs',
   use: {
      baseURL,
      browserName: 'chromium',
      viewport: { width: 1440, height: 1000 },
      trace: 'off',
      screenshot: 'only-on-failure',
   },
});
