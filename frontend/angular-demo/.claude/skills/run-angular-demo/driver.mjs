#!/usr/bin/env node
// Launches the Angular capabilities demo dev server, drives it with
// playwright-core (already present via the @playwright/test devDependency —
// no extra install needed), and exercises one representative interaction per
// a few key feature pages. Screenshots land in ./screenshots next to this
// script. Exits 0 if the app rendered with no console errors, 1 otherwise.
//
// Run from anywhere:
//   node .claude/skills/run-angular-demo/driver.mjs

import { chromium } from 'playwright-core';
import { spawn, execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, '../../..'); // .claude/skills/run-angular-demo -> angular-demo
const PORT = 4201;
const BASE_URL = `http://localhost:${PORT}`;
const SCREENSHOTS_DIR = path.join(__dirname, 'screenshots');

fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });

function waitForServer(url, timeoutMs = 40_000) {
  const deadline = Date.now() + timeoutMs;
  const attempt = async () => {
    try {
      await fetch(url);
    } catch {
      if (Date.now() > deadline) throw new Error(`Server did not become ready at ${url}`);
      await new Promise((r) => setTimeout(r, 1000));
      return attempt();
    }
  };
  return attempt();
}

// Kill any stale instance from a previous run before starting a fresh one.
try {
  execSync('pkill -f "ng serve" || true', { stdio: 'ignore' });
} catch {
  // ignore
}

const server = spawn('npm', ['start'], { cwd: PROJECT_ROOT, stdio: 'ignore', detached: true });

async function main() {
  await waitForServer(BASE_URL);

  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  const consoleErrors = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });
  page.on('pageerror', (err) => consoleErrors.push(String(err)));

  // Signals: verify signal/computed/effect reactivity.
  await page.goto(BASE_URL, { waitUntil: 'networkidle' });
  await page.waitForSelector('text=Signals');
  await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '01-signals.png') });

  await page.getByTestId('increment').click();
  await page.getByTestId('increment').click();
  await page.waitForFunction(() =>
    document.querySelector('[data-testid="count"]')?.textContent?.includes('2'),
  );
  await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '02-signals-incremented.png') });

  // Data Fetching: verify the real HttpClient round-trip against the static asset.
  await page.getByRole('link', { name: 'Data Fetching' }).click();
  await page.waitForSelector('[data-testid="items-list"]');
  await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '03-data-fetching.png') });

  // Animations: verify the native animate.enter binding fires.
  await page.getByRole('link', { name: 'Animations' }).click();
  await page.getByTestId('add-button').click();
  await page.waitForSelector('mat-list-item');
  await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '04-animations.png') });

  await browser.close();

  console.log('SCREENSHOTS:', SCREENSHOTS_DIR);
  console.log('CONSOLE_ERRORS:', JSON.stringify(consoleErrors));

  return consoleErrors.length === 0;
}

main()
  .then((ok) => {
    process.exitCode = ok ? 0 : 1;
  })
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(() => {
    try {
      process.kill(-server.pid, 'SIGTERM');
    } catch {
      // already dead
    }
    try {
      execSync('pkill -f "ng serve" || true', { stdio: 'ignore' });
    } catch {
      // ignore
    }
  });
