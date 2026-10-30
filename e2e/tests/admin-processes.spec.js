// Copyright (c) 2026 Kingsrook, LLC
// SPDX-License-Identifier: Apache-2.0
import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';

async function loginApi(request, account) {
   const password = process.env[account === 'admin' ? 'MOBILE_DEMO_ADMIN_PASSWORD' : 'MOBILE_DEMO_SCANNER_PASSWORD'];
   const response = await request.post('/qqq/v1/manageSession', {
      headers: { Authorization: `Basic ${Buffer.from(`${account}:${password}`).toString('base64')}` },
      data: {},
   });
   expect(response.status()).toBe(200);
}

async function enroll(request, name) {
   const response = await request.post('/mobile/v1/devices/enroll', {
      data: { installationId: randomUUID(), displayName: name, manufacturer: 'Test', model: 'Browser fixture', osVersion: 'test', appVersion: 'test' },
   });
   expect(response.ok()).toBeTruthy();
   return response.json();
}

async function loginBrowser(page) {
   // Authenticate through the real login endpoint without recording password-fill steps.
   await loginApi(page.request, 'admin');
   // QQQ only attempts HttpOnly-cookie resume when its non-secret sign-in hint exists.
   // The real server session still supplies the identity and permission checks.
   await page.addInitScript(() => localStorage.setItem('qqq.sessionHint', '1'));
   await page.goto('/app/');
   await expect(page.getByRole('button', { name: 'Account: Device Administrator' })).toBeVisible();
}

async function status(request, device) {
   const response = await request.get('/mobile/v1/devices/current', {
      headers: { 'X-QQQ-Device-Id': device.deviceId, 'X-QQQ-Device-Token': device.deviceToken },
   });
   expect(response.status()).toBe(200);
   return (await response.json()).status;
}

async function chooseAction(page, label, record = false) {
   await page.getByRole('button', { name: record ? 'Record actions menu' : 'Actions', exact: true }).click();
   await page.getByRole('menuitem', { name: label, exact: true }).click();
}

async function confirmSelected(page, devices, result) {
   const confirmation = page.locator('[data-qqq-id="process-step-input"]');
   for (const device of devices) await expect(confirmation).toContainText(device.deviceId);
   await confirmation.locator('[data-qqq-id="button-next"]').click();
   await expect(page.locator('[data-qqq-id="process-step-result"]')).toContainText(result);
}

test('approve and revoke from a single record show results and persist the selected device', async ({ page, request }) => {
   await loginApi(request, 'scanner');
   const device = await enroll(request, `Single ${randomUUID()}`);
   await loginBrowser(page);
   for (const [action, before, after, result] of [
      ['Approve Device', 'PENDING', 'APPROVED', '1 device approved.'],
      ['Revoke Device', 'APPROVED', 'REVOKED', '1 device revoked.'],
   ]) {
      await page.goto(`/app/mobileDevice/${device.deviceId}/`);
      await chooseAction(page, action, true);
      await expect.poll(() => status(request, device)).toBe(before);
      await confirmSelected(page, [device], result);
      await expect.poll(() => status(request, device)).toBe(after);
      await page.getByRole('button', { name: 'Return', exact: true }).click();
      await expect(page).toHaveURL(new RegExp(`/app/mobileDevice/${device.deviceId}/?$`));
      await expect(page.getByRole('tabpanel', { name: 'Overview' })).toContainText(after);
   }
});

test('bulk checked rows approve and revoke only selected devices', async ({ page, request }) => {
   await loginApi(request, 'scanner');
   const devices = [await enroll(request, `Bulk A ${randomUUID()}`), await enroll(request, `Bulk B ${randomUUID()}`)];
   const untouched = await enroll(request, `Untouched ${randomUUID()}`);
   await loginBrowser(page);
   for (const [action, before, after, result] of [
      ['Approve Device', 'PENDING', 'APPROVED', '2 devices approved.'],
      ['Revoke Device', 'APPROVED', 'REVOKED', '2 devices revoked.'],
   ]) {
      await page.goto('/app/mobileDevice/');
      for (const device of devices) await page.getByRole('checkbox', { name: `Select ${device.deviceId}`, exact: true }).check();
      await expect(page.getByRole('region', { name: 'Bulk actions' })).toContainText('2 records');
      await chooseAction(page, action);
      for (const device of devices) await expect.poll(() => status(request, device)).toBe(before);
      await confirmSelected(page, devices, result);
      for (const device of devices) await expect.poll(() => status(request, device)).toBe(after);
      await expect.poll(() => status(request, untouched)).toBe('PENDING');
   }
});

test('standalone admin processes accept an explicit device ID', async ({ page, request }) => {
   await loginApi(request, 'scanner');
   const device = await enroll(request, `Manual ${randomUUID()}`);
   await loginBrowser(page);
   for (const [process, after, result] of [
      ['mobileDeviceApprove', 'APPROVED', '1 device approved.'],
      ['mobileDeviceRevoke', 'REVOKED', '1 device revoked.'],
   ]) {
      await page.goto(`/app/${process}/`);
      await page.getByRole('textbox', { name: 'Device IDs', exact: true }).fill(device.deviceId);
      await page.locator('[data-qqq-id="button-next"]').click();
      await expect(page.locator('[data-qqq-id="process-step-result"]')).toContainText(result);
      await expect.poll(() => status(request, device)).toBe(after);
   }
});

test('cancel from record confirmation leaves device pending', async ({ page, request }) => {
   await loginApi(request, 'scanner');
   const device = await enroll(request, `Cancel ${randomUUID()}`);
   await loginBrowser(page);
   await page.goto(`/app/mobileDevice/${device.deviceId}/`);
   await chooseAction(page, 'Approve Device', true);
   await expect(page.locator('[data-qqq-id="process-step-input"]')).toContainText(device.deviceId);
   await page.getByRole('button', { name: 'Cancel', exact: true }).click();
   await page.getByRole('dialog', { name: 'Cancel Process?' }).getByRole('button', { name: 'Cancel Process', exact: true }).click();
   await expect.poll(() => status(request, device)).toBe('PENDING');
});

test('full query selection approves and revokes through the filter-based bulk path', async ({ page, request }) => {
   await loginApi(request, 'scanner');
   const devices = [await enroll(request, `All A ${randomUUID()}`), await enroll(request, `All B ${randomUUID()}`)];
   await loginBrowser(page);
   for (const [action, after, verb] of [
      ['Approve Device', 'APPROVED', 'approved'],
      ['Revoke Device', 'REVOKED', 'revoked'],
   ]) {
      await page.goto('/app/mobileDevice/');
      await page.getByRole('button', { name: 'Selection', exact: true }).click();
      await page.getByRole('menuitem', { name: /^Full query result/ }).click();
      await chooseAction(page, action);
      const confirmation = page.locator('[data-qqq-id="process-step-input"]');
      for (const device of devices) await expect(confirmation).toContainText(device.deviceId);
      await confirmation.locator('[data-qqq-id="button-next"]').click();
      await expect(page.locator('[data-qqq-id="process-step-result"]')).toContainText(new RegExp(`\\d+ devices ${verb}\\.`));
      for (const device of devices) await expect.poll(() => status(request, device)).toBe(after);
   }
});

test('first-N bulk selection changes exactly the requested subset', async ({ page, request }) => {
   await loginApi(request, 'scanner');
   await enroll(request, `Subset A ${randomUUID()}`);
   await enroll(request, `Subset B ${randomUUID()}`);
   await enroll(request, `Subset C ${randomUUID()}`);
   await loginBrowser(page);
   for (const [action, after, result] of [
      ['Approve Device', 'APPROVED', '2 devices approved.'],
      ['Revoke Device', 'REVOKED', '2 devices revoked.'],
   ]) {
      const beforeResponse = await page.request.post('/qqq/v1/table/mobileDevice/query', { data: { filter: { limit: 100 } } });
      expect(beforeResponse.status()).toBe(200);
      const before = new Map((await beforeResponse.json()).records.map((record) => [record.values.id, record.values.status]));
      await page.goto('/app/mobileDevice/');
      await page.getByRole('button', { name: 'Selection', exact: true }).click();
      await page.getByRole('menuitem', { name: 'Subset of the query result...' }).click();
      await page.getByRole('spinbutton', { name: 'How many records do you want to select?' }).fill('2');
      await page.getByRole('button', { name: 'OK', exact: true }).click();
      await chooseAction(page, action);
      const confirmation = page.locator('[data-qqq-id="process-step-input"]');
      await expect(confirmation).toContainText('2 device(s) selected');
      const summary = await confirmation.innerText();
      const selected = [...before.keys()].filter((id) => summary.includes(id));
      expect(selected).toHaveLength(2);
      await confirmation.locator('[data-qqq-id="button-next"]').click();
      await expect(page.locator('[data-qqq-id="process-step-result"]')).toContainText(result);
      const afterResponse = await page.request.post('/qqq/v1/table/mobileDevice/query', { data: { filter: { limit: 100 } } });
      expect(afterResponse.status()).toBe(200);
      for (const record of (await afterResponse.json()).records) {
         expect(record.values.status).toBe(selected.includes(record.values.id) ? after : before.get(record.values.id));
      }
   }
});
