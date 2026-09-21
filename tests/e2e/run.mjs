import { chromium } from 'playwright';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const baseUrl = process.env.CERTMUSE_E2E_BASE_URL ?? 'http://frontend';
const reportsDir = process.env.CERTMUSE_E2E_REPORTS_DIR ?? '/workspace/reports';
const admin = {
  username: process.env.CERTMUSE_E2E_ADMIN_USERNAME ?? 'admin',
  password: process.env.CERTMUSE_E2E_ADMIN_PASSWORD ?? 'admin123'
};
const learner = {
  username: `e2e_student_${Date.now()}`,
  password: 'CertMuseE2E!2026'
};
const manifest = JSON.parse(await readFile(new URL('./page-manifest.json', import.meta.url), 'utf8'));
const report = {
  startedAt: new Date().toISOString(),
  baseUrl,
  browser: 'chromium',
  cases: [],
  consoleErrors: [],
  networkFailures: []
};

await mkdir(reportsDir, { recursive: true });

const technicalError = body => /(?:Cannot read properties of|TypeError:|ReferenceError:|ChunkLoadError)/i.test(body);
const record = async (id, status, message, page) => {
  const evidence = `${id}-${status}.png`;
  if (page) await page.screenshot({ path: join(reportsDir, evidence), fullPage: true }).catch(() => {});
  report.cases.push({ id, status, message, evidence });
};
const assert = (condition, message) => {
  if (!condition) throw new Error(message);
};
const visit = async (page, path) => {
  const response = await page.goto(`${baseUrl}${path}`, { waitUntil: 'networkidle', timeout: 30_000 });
  assert(response?.ok(), `${path} returned HTTP ${response?.status()}`);
  const body = await page.locator('body').innerText();
  assert(!technicalError(body), `${path} rendered a technical error`);
  return body;
};
const login = async (page, account) => {
  await visit(page, '/login');
  await page.getByPlaceholder('用户名').fill(account.username);
  await page.getByPlaceholder('密码').fill(account.password);
  await page.getByRole('button', { name: /登录/ }).click();
  await page.waitForURL(url => !url.pathname.endsWith('/login'), { timeout: 30_000 });
  assert(!page.url().includes('/learning/'), 'administrator was incorrectly routed to the learner application');
};

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext();
await context.tracing.start({ screenshots: true, snapshots: true, sources: true });
context.on('requestfailed', request => report.networkFailures.push({ url: request.url(), failure: request.failure()?.errorText }));
const page = await context.newPage();
page.on('console', message => {
  if (message.type() === 'error') report.consoleErrors.push(message.text());
});

try {
  for (const entry of manifest.pages.filter(item => item.kind === 'public')) {
    try {
      const body = await visit(page, entry.path);
      assert(body.includes(entry.text), `${entry.path} did not show expected text: ${entry.text}`);
      await record(entry.id, 'passed', `${entry.path} rendered its expected public state`, page);
    } catch (error) {
      await record(entry.id, 'blocking_failure', error.message, page);
    }
  }

  try {
    await visit(page, '/login');
    await page.getByPlaceholder('用户名').fill('invalid-e2e-user');
    await page.getByPlaceholder('密码').fill('wrong-password');
    await page.getByRole('button', { name: /登录/ }).click();
    await page.waitForTimeout(500);
    assert(page.url().includes('/login'), 'invalid credentials created a navigable session');
    await record('AUTH-FAIL-001', 'passed', 'invalid credentials stay on the login boundary', page);
  } catch (error) {
    await record('AUTH-FAIL-001', 'blocking_failure', error.message, page);
  }

  for (const entry of manifest.pages.filter(item => item.kind === 'student-guest')) {
    try {
      const body = await visit(page, entry.path);
      const protectedByLogin = page.url().includes('/login');
      const publicReadModel = body.includes('历年真题') || body.includes('登录后使用');
      assert(protectedByLogin || publicReadModel, `${entry.path} exposed an unexpected unauthenticated state`);
      await record(entry.id, 'passed', protectedByLogin ? 'guest redirected to authentication' : 'public read model rendered', page);
    } catch (error) {
      await record(entry.id, 'blocking_failure', error.message, page);
    }
  }

  try {
    await login(page, admin);
    await record('AUTH-OK-001', 'passed', 'administrator login reached an administrative route', page);
  } catch (error) {
    await record('AUTH-OK-001', 'blocking_failure', error.message, page);
  }

  try {
    await visit(page, '/register');
    await page.getByPlaceholder('设置登录账号（2–30 个字符）').fill(learner.username);
    await page.getByPlaceholder('设置密码（8–30 个字符）').fill(learner.password);
    await page.getByPlaceholder('再次输入密码').fill(learner.password);
    await page.getByRole('button', { name: '注册学员账号' }).click();
    await page.waitForURL(url => url.pathname.endsWith('/login') && url.searchParams.get('registered') === '1', { timeout: 30_000 });
    await record('AUTH-REG-001', 'passed', 'learner registration returns to login without issuing a browser session', page);
  } catch (error) {
    await record('AUTH-REG-001', 'blocking_failure', error.message, page);
  }

  try {
    await login(page, learner);
    assert(page.url().includes('/learning/'), 'learner was not routed into the learning application');
    const body = await page.locator('body').innerText();
    assert(!technicalError(body), 'learner entry route rendered a technical error');
    await record('STU-AUTH-001', 'passed', 'registered learner enters the learning application', page);
  } catch (error) {
    await record('STU-AUTH-001', 'blocking_failure', error.message, page);
  }

  for (const entry of manifest.pages.filter(item => item.kind === 'student-guest').slice(0, 12)) {
    try {
      const body = await visit(page, entry.path);
      assert(!page.url().includes('/admin/'), `${entry.path} crossed into an administrative route`);
      assert(!technicalError(body), `${entry.path} rendered a technical error for its owner`);
      await record(`${entry.id}-AUTH`, 'passed', 'learner route has a controlled authenticated state', page);
    } catch (error) {
      await record(`${entry.id}-AUTH`, 'blocking_failure', error.message, page);
    }
  }

  try {
    await login(page, admin);
    await record('AUTH-OK-002', 'passed', 'administrator session was restored before admin route coverage', page);
  } catch (error) {
    await record('AUTH-OK-002', 'blocking_failure', error.message, page);
  }

  for (const entry of manifest.pages.filter(item => item.kind === 'admin')) {
    try {
      const body = await visit(page, entry.path);
      assert(!page.url().includes('/login'), `${entry.path} rejected the seeded administrator`);
      assert(body.includes(entry.text), `${entry.path} did not show expected text: ${entry.text}`);
      await record(entry.id, 'passed', `${entry.path} rendered for an authorised administrator`, page);
    } catch (error) {
      await record(entry.id, 'blocking_failure', error.message, page);
    }
  }

  for (const entry of manifest.pages.filter(item => item.kind === 'legacy-redirect')) {
    try {
      const body = await visit(page, entry.path);
      assert(new URL(page.url()).pathname === entry.target, `${entry.path} did not redirect to ${entry.target}`);
      assert(body.includes(entry.text), `${entry.target} did not render its implemented management page`);
      await record(entry.id, 'passed', `${entry.path} redirected to ${entry.target}`, page);
    } catch (error) {
      await record(entry.id, 'blocking_failure', error.message, page);
    }
  }

  for (const entry of manifest.pages.filter(item => item.kind === 'expected-defect')) {
    try {
      const menu = page.getByText(entry.menuText, { exact: true }).first();
      await menu.click({ timeout: 10_000 });
      await page.waitForLoadState('networkidle');
      const body = await page.locator('body').innerText();
      assert(body.includes(entry.text), `${entry.menuText} no longer exposes the tracked placeholder`);
      await record(entry.id, 'expected_defect', `${entry.defectId}: ${entry.text}`, page);
    } catch (error) {
      await record(entry.id, 'blocking_failure', `${entry.defectId}: ${error.message}`, page);
    }
  }
} finally {
  await context.tracing.stop({ path: join(reportsDir, 'playwright-trace.zip') }).catch(() => {});
  await browser.close();
}

report.finishedAt = new Date().toISOString();
report.summary = report.cases.reduce((summary, item) => {
  summary[item.status] = (summary[item.status] ?? 0) + 1;
  return summary;
}, {});
report.passed = !report.cases.some(item => item.status === 'blocking_failure');
const markdown = [
  '# CertMuse Chromium E2E report',
  '',
  `- Started: ${report.startedAt}`,
  `- Finished: ${report.finishedAt}`,
  `- Result: ${report.passed ? 'passed' : 'blocking failures found'}`,
  '',
  '| Case | Status | Evidence | Message |',
  '| --- | --- | --- | --- |',
  ...report.cases.map(item => `| ${item.id} | ${item.status} | ${item.evidence} | ${item.message.replaceAll('|', '\\|')} |`),
  '',
  `Network failures: ${report.networkFailures.length}`,
  `Console errors: ${report.consoleErrors.length}`
].join('\n');
await writeFile(join(reportsDir, 'e2e-result.json'), `${JSON.stringify(report, null, 2)}\n`);
await writeFile(join(reportsDir, 'E2E-REPORT.md'), `${markdown}\n`);
if (!report.passed) process.exitCode = 1;
