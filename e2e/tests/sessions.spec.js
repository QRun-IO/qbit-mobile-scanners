// Copyright (c) 2026 Kingsrook, LLC
// SPDX-License-Identifier: Apache-2.0
import { randomUUID } from 'node:crypto';
import { test, expect, request as requestFactory } from '@playwright/test';

const extension = '/qqq/v1/qbit/mobile-scanners';

async function login(request, account) {
   const password = process.env[account === 'admin' ? 'MOBILE_DEMO_ADMIN_PASSWORD' : 'MOBILE_DEMO_SCANNER_PASSWORD'];
   const response = await request.post('/qqq/v1/manageSession', {
      headers: { Authorization: `Basic ${Buffer.from(`${account}:${password}`).toString('base64')}` }, data: {},
   });
   expect(response.status()).toBe(200);
}

async function admin(page) {
   await login(page.request, 'admin');
   await page.addInitScript(() => localStorage.setItem('qqq.sessionHint', '1'));
   await page.goto('/app/');
   await expect(page.getByRole('button', { name: 'Account: Device Administrator' })).toBeVisible();
}

async function complete(request, process, response) {
   expect(response.status()).toBe(200);
   let body = await response.json();
   const processId = body.processUUID;
   const jobId = body.jobUUID;
   await expect.poll(async () => {
      if (['JOB_STARTED', 'RUNNING'].includes(body.type)) {
         const status = await request.get(`/qqq/v1/processes/${process}/${processId}/status/${jobId}`);
         expect(status.status()).toBe(200);
         body = await status.json();
      }
      return ['JOB_STARTED', 'RUNNING'].includes(body.type);
   }).toBe(false);
   expect(body.error).toBeFalsy();
   return body;
}

async function approve(request, deviceId) {
   const process = 'mobileDeviceApprove';
   const selection = { recordsParam: 'recordIds', recordIds: deviceId, tableName: 'mobileDevice' };
   let body = await complete(request, process, await request.post(`/qqq/v1/processes/${process}/init`, {
      multipart: { values: JSON.stringify(selection), recordsParam: 'recordIds', recordIds: deviceId, stepTimeoutMillis: '1' },
   }));
   for (const step of ['input', 'result']) {
      expect(body.nextStep).toBe(step);
      body = await complete(request, process, await request.post(`/qqq/v1/processes/${process}/${body.processUUID}/step/${step}`, {
         multipart: { values: '{}', stepTimeoutMillis: '1' },
      }));
   }
}

async function scanner(page, baseURL) {
   // Each handheld has a separate authenticated cookie jar, even for the same user.
   const request = await requestFactory.newContext({ baseURL });
   try {
      await login(request, 'scanner');
      const response = await request.post(`${extension}/devices/enroll`, {
         data: { installationId: randomUUID(), displayName: `Session E2E ${randomUUID()}`,
            manufacturer: 'Test', model: 'Browser session fixture', osVersion: 'test', appVersion: 'test' },
      });
      expect(response.status()).toBe(201);
      const device = await response.json();
      await approve(page.request, device.deviceId);
      const headers = { 'X-QQQ-Device-Id': device.deviceId, 'X-QQQ-Device-Token': device.deviceToken,
         'X-QQQ-Client': 'android/qqq-mobile' };
      const heartbeat = (values = {}) => request.post(`${extension}/sessions/heartbeat`, {
         headers, data: { foreground: true, screenAvailable: true, appName: 'mobileScanApp',
            processName: 'mobileScanCapture', stepName: 'scan', screenLabel: 'Scan Capture', ...values },
      });
      const started = await heartbeat();
      expect(started.status()).toBe(200);
      const session = await started.json();
      return { request, headers, heartbeat, sessionId: session.sessionId, deviceId: device.deviceId };
   } catch (error) {
      await request.dispose();
      throw error;
   }
}

async function sessions(request) {
   const response = await request.post('/qqq/v1/table/mobileSession/query', { data: { filter: { limit: 100 } } });
   expect(response.status()).toBe(200);
   return (await response.json()).records.map((record) => record.values);
}

async function state(request, id) {
   const record = (await sessions(request)).find((row) => row.id === id);
   expect(record).toBeDefined();
   return record;
}

/** Read the persisted profile independently of the browser's form state. */
async function deviceState(request, id) {
   const response = await request.post('/qqq/v1/table/mobileDevice/query', {
      data: { filter: { criteria: [{ fieldName: 'id', operator: 'EQUALS', values: [id] }], limit: 1 } },
   });
   expect(response.status()).toBe(200);
   const records = (await response.json()).records;
   expect(records).toHaveLength(1);
   return records[0].values;
}

async function chooseEnd(page, record = false) {
   await page.getByRole('button', { name: record ? 'Record actions menu' : 'Actions', exact: true }).click();
   await page.getByRole('menuitem', { name: 'End Session', exact: true }).click();
}

async function confirmEnd(page, ids) {
   const confirmation = page.locator('[data-qqq-id="process-step-input"]');
   for (const id of ids) await expect(confirmation).toContainText(id);
   await confirmation.locator('[data-qqq-id="button-next"]').click();
   await expect(page.locator('[data-qqq-id="process-step-result"]'))
      .toContainText(`${ids.length} session${ids.length === 1 ? '' : 's'} ended.`);
}

test('session record termination requires confirmation, audits the actor and rejects cookie replay', async ({ page, baseURL }) => {
   await admin(page);
   const target = await scanner(page, baseURL);
   const untouched = await scanner(page, baseURL);
   try {
      await page.goto(`/app/mobileSession/${target.sessionId}/`);
      await chooseEnd(page, true);
      expect((await state(page.request, target.sessionId)).status).toBe('ACTIVE');
      await confirmEnd(page, [target.sessionId]);
      const ended = await state(page.request, target.sessionId);
      expect(ended.status).toBe('ENDED');
      expect(ended.endedBy).toBe('admin');
      expect(ended.endedAt).toBeTruthy();
      expect((await target.heartbeat()).status()).toBe(401);
      // Omission of device headers must not resurrect an ended core session.
      expect((await target.request.get('/qqq/v1/metaData')).status()).toBe(401);
      expect((await untouched.heartbeat()).status()).toBe(200);
      await page.getByRole('button', { name: 'Return', exact: true }).click();
      await expect(page).toHaveURL(new RegExp(`/app/mobileSession/${target.sessionId}/?$`));
      await expect(page.getByRole('tabpanel', { name: 'Overview' })).toContainText('ENDED');
   } finally {
      await Promise.all([target.request.dispose(), untouched.request.dispose()]);
   }
});

test('checked bulk session termination leaves an unselected session usable', async ({ page, baseURL }) => {
   await admin(page);
   const devices = [];
   try {
      for (let index = 0; index < 3; index++) devices.push(await scanner(page, baseURL));
      await page.goto('/app/mobileSession/');
      for (const device of devices.slice(0, 2)) {
         await page.getByRole('checkbox', { name: `Select ${device.sessionId}`, exact: true }).check();
      }
      await expect(page.getByRole('region', { name: 'Bulk actions' })).toContainText('2 records');
      await chooseEnd(page);
      await confirmEnd(page, devices.slice(0, 2).map((device) => device.sessionId));
      for (const device of devices.slice(0, 2)) {
         expect((await state(page.request, device.sessionId)).status).toBe('ENDED');
         expect((await device.heartbeat()).status()).toBe(401);
      }
      expect((await state(page.request, devices[2].sessionId)).status).toBe('ACTIVE');
      expect((await devices[2].heartbeat()).status()).toBe(200);
   } finally {
      await Promise.all(devices.map((device) => device.request.dispose()));
   }
});

test('cancelling session termination preserves access', async ({ page, baseURL }) => {
   await admin(page);
   const target = await scanner(page, baseURL);
   try {
      await page.goto(`/app/mobileSession/${target.sessionId}/`);
      await chooseEnd(page, true);
      await expect(page.locator('[data-qqq-id="process-step-input"]')).toContainText(target.sessionId);
      await page.getByRole('button', { name: 'Cancel', exact: true }).click();
      await page.getByRole('dialog', { name: 'Cancel Process?' }).getByRole('button', { name: 'Cancel Process', exact: true }).click();
      expect((await state(page.request, target.sessionId)).status).toBe('ACTIVE');
      expect((await target.heartbeat()).status()).toBe(200);
   } finally {
      await target.request.dispose();
   }
});

test('standalone session termination accepts an explicit public session ID', async ({ page, baseURL }) => {
   await admin(page);
   const target = await scanner(page, baseURL);
   try {
      await page.goto('/app/mobileSessionEnd/');
      await page.getByRole('textbox', { name: 'Session IDs', exact: true }).fill(target.sessionId);
      await page.locator('[data-qqq-id="button-next"]').click();
      await expect(page.locator('[data-qqq-id="process-step-result"]')).toContainText('1 session ended.');
      expect((await state(page.request, target.sessionId)).status).toBe('ENDED');
      expect((await target.heartbeat()).status()).toBe(401);
   } finally {
      await target.request.dispose();
   }
});

test('first-N bulk session termination preserves every unselected record', async ({ page, baseURL }) => {
   await admin(page);
   const devices = [];
   try {
      for (let index = 0; index < 3; index++) devices.push(await scanner(page, baseURL));
      const before = new Map((await sessions(page.request)).map((row) => [row.id, row.status]));
      await page.goto('/app/mobileSession/');
      await page.getByRole('button', { name: 'Selection', exact: true }).click();
      await page.getByRole('menuitem', { name: 'Subset of the query result...' }).click();
      await page.getByRole('spinbutton', { name: 'How many records do you want to select?' }).fill('2');
      await page.getByRole('button', { name: 'OK', exact: true }).click();
      await chooseEnd(page);
      const confirmation = page.locator('[data-qqq-id="process-step-input"]');
      await expect(confirmation).toContainText('2 session(s) selected');
      const summary = await confirmation.innerText();
      const selected = [...before.keys()].filter((id) => summary.includes(id));
      expect(selected).toHaveLength(2);
      await confirmEnd(page, selected);
      for (const row of await sessions(page.request)) {
         expect(row.status).toBe(selected.includes(row.id) ? 'ENDED' : before.get(row.id));
      }
      for (const device of devices) {
         expect((await device.heartbeat()).status()).toBe(selected.includes(device.sessionId) ? 401 : 200);
      }
   } finally {
      await Promise.all(devices.map((device) => device.request.dispose()));
   }
});

test('full-query session termination ends the reviewed query result', async ({ page, baseURL }) => {
   await admin(page);
   const target = await scanner(page, baseURL);
   try {
      const before = await sessions(page.request);
      await page.goto('/app/mobileSession/');
      await page.getByRole('button', { name: 'Selection', exact: true }).click();
      await page.getByRole('menuitem', { name: /^Full query result/ }).click();
      await chooseEnd(page);
      await confirmEnd(page, before.map((row) => row.id));
      for (const row of await sessions(page.request)) expect(row.status).toBe('ENDED');
      expect((await target.heartbeat()).status()).toBe(401);
   } finally {
      await target.request.dispose();
   }
});

test('native record widget requests, refreshes and clears a leased screen image', async ({ page, baseURL }) => {
   await admin(page);
   const target = await scanner(page, baseURL);
   try {
      const before = await (await target.heartbeat()).json();
      expect(before.screenMirror).toBeFalsy();
      await page.goto(`/app/mobileSession/${target.sessionId}/`);
      let demand;
      await expect.poll(async () => {
         demand = await (await target.heartbeat()).json();
         return Boolean(demand.screenMirror?.leaseId);
      }).toBe(true);
      const imageBase64 = await page.evaluate(() => {
         const canvas = document.createElement('canvas');
         canvas.width = 160;
         canvas.height = 240;
         const context = canvas.getContext('2d');
         context.fillStyle = '#0969da';
         context.fillRect(0, 0, 160, 240);
         context.fillStyle = '#ffffff';
         context.font = '18px sans-serif';
         context.fillText('Screen fixture', 12, 80);
         return canvas.toDataURL('image/jpeg').split(',')[1];
      });
      const upload = await target.request.post(`${extension}/sessions/current/screen`, {
         headers: target.headers, data: { leaseId: demand.screenMirror.leaseId,
            sequence: demand.screenMirror.nextSequence, capturedAt: new Date().toISOString(),
            width: 160, height: 240, mimeType: 'image/jpeg', imageBase64 },
      });
      expect(upload.status()).toBe(204);
      const image = page.locator('img[src^="data:image/jpeg;base64,"]');
      await expect(image).toHaveAttribute('src', `data:image/jpeg;base64,${imageBase64}`);
      await expect(image).toBeVisible();
      const background = await target.heartbeat({ foreground: false, screenAvailable: false, captureBlockedReason: 'BACKGROUND' });
      expect(background.status()).toBe(200);
      await expect(image).toHaveCount(0);
      await page.goto(`/app/mobileDevice/${target.deviceId}/`);
      await page.getByRole('tab', { name: 'Sessions', exact: true }).click();
      await expect(page.getByRole('tabpanel', { name: 'Sessions', exact: true })
         .locator(`a[href="/app/mobileSession/${target.sessionId}/"]`)).toBeVisible();
   } finally {
      await target.request.dispose();
   }
});

test('device record editing saves names and description while preserving approval', async ({ page, baseURL }) => {
   await admin(page);
   const target = await scanner(page, baseURL);
   try {
      const initial = await deviceState(page.request, target.deviceId);
      expect(initial.shortName).toMatch(/^scanner-[a-f0-9]{12}$/);
      const shortName = `receiving-${randomUUID().slice(0, 8)}`;
      await page.goto(`/app/mobileDevice/${target.deviceId}/edit/`);
      await page.getByRole('textbox', { name: 'Short Name', exact: true }).fill(shortName);
      await page.getByRole('textbox', { name: 'Display Name', exact: true }).fill('Receiving handheld');
      await page.getByRole('textbox', { name: 'Description', exact: true }).fill('Assigned to receiving station 2');
      await expect(page.getByRole('textbox', { name: 'Approval Status', exact: true })).toHaveCount(0);
      await page.locator('[data-qqq-id="button-save"]').click();
      await expect(page).toHaveURL(new RegExp(`/app/mobileDevice/${target.deviceId}/?$`));
      const saved = await deviceState(page.request, target.deviceId);
      expect(saved.shortName).toBe(shortName);
      expect(saved.displayName).toBe('Receiving handheld');
      expect(saved.description).toBe('Assigned to receiving station 2');
      expect(saved.status).toBe('APPROVED');
      expect(saved.approvedBy).toBe(initial.approvedBy);
      expect(saved.approvedAt).toBe(initial.approvedAt);
      const overview = page.getByRole('tabpanel', { name: 'Overview', exact: true });
      await expect(overview).toContainText(shortName);
      await expect(overview).toContainText('Assigned to receiving station 2');
      expect((await target.heartbeat()).status()).toBe(200);
   } finally {
      await target.request.dispose();
   }
});

test('device location stays attributable and online presence expires independently of approval', async ({ page, baseURL }) => {
   await admin(page);
   const target = await scanner(page, baseURL);
   try {
      // Synthetic coordinates, never the operator's physical location.
      const capturedAt = new Date().toISOString();
      expect((await target.heartbeat({ locationStatus: 'AVAILABLE', location: {
         latitude: 10.125, longitude: 20.25, accuracyMeters: 7, capturedAt,
      } })).status()).toBe(200);
      let saved = await deviceState(page.request, target.deviceId);
      expect(saved.status).toBe('APPROVED');
      expect(saved.online).toBe(true);
      expect(Number(saved.latitude)).toBe(10.125);
      expect(Number(saved.longitude)).toBe(20.25);
      expect(Number(saved.accuracyMeters)).toBe(7);
      expect(Date.parse(saved.locationCapturedAt)).toBe(Date.parse(capturedAt));
      expect(saved.locationReceivedAt).toBeTruthy();
      await page.goto(`/app/mobileDevice/${target.deviceId}/`);
      const overview = page.getByRole('tabpanel', { name: 'Overview', exact: true });
      await expect(overview).toContainText('AVAILABLE');
      await expect(overview).toContainText('10.125');
      expect((await target.heartbeat({ locationStatus: 'PERMISSION_DENIED' })).status()).toBe(200);
      await page.reload();
      await expect(overview).toContainText('PERMISSION_DENIED');
      saved = await deviceState(page.request, target.deviceId);
      expect(Number(saved.latitude)).toBe(10.125);
      expect(Date.parse(saved.locationCapturedAt)).toBe(Date.parse(capturedAt));
      await expect.poll(async () => (await deviceState(page.request, target.deviceId)).online,
         { timeout: 25_000, intervals: [1000] }).toBe(false);
      await expect(overview).toContainText('Connection: Offline');
      expect((await deviceState(page.request, target.deviceId)).status).toBe('APPROVED');
      const online = await page.request.post('/qqq/v1/table/mobileDevice/query', {
         data: { filter: { criteria: [{ fieldName: 'online', operator: 'EQUALS', values: [true] }] } },
      });
      expect(online.status()).toBe(200);
      expect((await online.json()).records.some((row) => row.values.id === target.deviceId)).toBe(false);
      expect((await target.heartbeat()).status()).toBe(200);
      expect((await deviceState(page.request, target.deviceId)).online).toBe(true);
      await expect(overview).toContainText('Connection: Online');
   } finally {
      await target.request.dispose();
   }
});
