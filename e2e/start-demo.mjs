// Copyright (c) 2026 Kingsrook, LLC
// SPDX-License-Identifier: Apache-2.0
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/** Start one real demo with isolated storage; return awaited shutdown and cleanup. */
export async function startDemo() {
   const port = Number(process.env.MOBILE_E2E_PORT);
   if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Set MOBILE_E2E_PORT to an unoccupied test port.');
   const url = `http://127.0.0.1:${port}/qqq/v1/qbit/mobile-scanners/status`;
   try {
      await fetch(url, { signal: AbortSignal.timeout(500) });
      throw new Error('Test port is already in use; refusing to reuse a server.');
   } catch (error) {
      if (error.message.startsWith('Test port')) throw error;
   }
   const repository = fileURLToPath(new URL('../', import.meta.url));
   const classpath = [resolve(repository, 'demo/target/classes'), (await readFile(resolve(repository, 'demo/target/e2e-classpath.txt'), 'utf8')).trim()].join(delimiter);
   const directory = await mkdtemp(join(tmpdir(), 'qqq-mobile-e2e-'));
   const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
   const child = spawn(java, ['-cp', classpath, 'com.kingsrook.qbits.mobilescanners.demo.MobileScannersDemo', String(port)], {
      cwd: directory,
      stdio: 'ignore',
      windowsHide: true,
   });
   let failure;
   const exited = new Promise((done) => {
      child.once('error', (error) => { failure = error.code; done(); });
      child.once('exit', (code) => { failure = `exit ${code}`; done(); });
   });
   const cleanup = async () => {
      child.kill();
      await exited;
      const target = resolve(directory);
      if (dirname(target) !== resolve(tmpdir()) || !basename(target).startsWith('qqq-mobile-e2e-')) throw new Error('Refusing unexpected test cleanup path.');
      await rm(target, { recursive: true, force: true, maxRetries: 3 });
   };
   try {
      const deadline = Date.now() + 60_000;
      while (Date.now() < deadline) {
         if (failure) throw new Error(`Isolated demo startup failed: ${failure}`);
         try {
            if ((await fetch(url, { signal: AbortSignal.timeout(1000) })).ok) return cleanup;
         } catch { /* Wait for the child server, not an unrelated persistent demo. */ }
         await new Promise((done) => setTimeout(done, 200));
      }
      throw new Error('Isolated demo startup timed out.');
   } catch (error) {
      await cleanup();
      throw error;
   }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
   const cleanup = await startDemo();
   for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, async () => { await cleanup(); process.exit(0); });
}
