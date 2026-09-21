import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';

const baseUrl = process.env.CERTMUSE_E2E_BASE_URL ?? 'http://frontend';
const reports = '/workspace/reports';
await mkdir(reports, { recursive: true });

const result = {
  startedAt: new Date().toISOString(),
  baseUrl,
  assertions: []
};

function check(condition, name, details) {
  const assertion = { name, passed: Boolean(condition), details };
  result.assertions.push(assertion);
  if (!condition) throw new Error(`${name}: ${JSON.stringify(details)}`);
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext();
const page = await context.newPage();

try {
  const consoleErrors = [];
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });

  const response = await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' });
  check(response?.ok(), 'login page returns a successful response', { status: response?.status() });
  await page.getByPlaceholder('用户名').waitFor();
  await page.getByPlaceholder('密码').waitFor();
  check(await page.getByRole('button', { name: '登 录' }).isVisible(), 'login button is visible');

  const authResponse = await context.request.get(`${baseUrl}/prod-api/auth/code`);
  check(authResponse.ok(), 'Nginx proxies the authentication endpoint', { status: authResponse.status() });

  await page.screenshot({ path: `${reports}/login-page.png`, fullPage: true });
  result.consoleErrors = consoleErrors;
  result.finishedAt = new Date().toISOString();
  result.passed = result.assertions.every(assertion => assertion.passed);
  await writeFile(`${reports}/smoke-result.json`, `${JSON.stringify(result, null, 2)}\n`);
} catch (error) {
  result.finishedAt = new Date().toISOString();
  result.passed = false;
  result.error = { message: error.message, stack: error.stack };
  await page.screenshot({ path: `${reports}/failure.png`, fullPage: true }).catch(() => {});
  await writeFile(`${reports}/smoke-result.json`, `${JSON.stringify(result, null, 2)}\n`);
  throw error;
} finally {
  await browser.close();
}

