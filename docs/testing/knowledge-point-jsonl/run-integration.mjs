import { createCipheriv, createDecipheriv, privateDecrypt, publicEncrypt, randomBytes, constants } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { basename, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const fixtures = join(here, 'fixtures');
const results = process.env.CERTMUSE_TEST_RESULTS_DIR
  ? resolve(process.env.CERTMUSE_TEST_RESULTS_DIR)
  : join(here, 'results');
const baseUrl = process.env.CERTMUSE_TEST_BASE_URL ?? 'http://localhost:18080';
const frontendEnv = await readFile(join(here, '../../../web/admin/.env.production'), 'utf8');
const envValue = name => frontendEnv.match(new RegExp(`^${name}\\s*=\\s*'([^']+)'`, 'm'))?.[1];
const publicKeyBody = envValue('VITE_APP_RSA_PUBLIC_KEY');
const privateKeyBody = envValue('VITE_APP_RSA_PRIVATE_KEY');
const publicKey = `-----BEGIN PUBLIC KEY-----\n${publicKeyBody.match(/.{1,64}/g).join('\n')}\n-----END PUBLIC KEY-----`;
const privateKey = `-----BEGIN PRIVATE KEY-----\n${privateKeyBody.match(/.{1,64}/g).join('\n')}\n-----END PRIVATE KEY-----`;
const report = { startedAt: new Date().toISOString(), baseUrl, assertions: [], batches: {} };

function check(condition, name, details = undefined) {
  report.assertions.push({ name, passed: Boolean(condition), details });
  if (!condition) throw new Error(`${name}: ${JSON.stringify(details)}`);
}

async function parseResponse(response) {
  let text = await response.text();
  const encryptedKey = response.headers.get('encrypt-key');
  if (encryptedKey) {
    const keyBase64 = privateDecrypt(
      { key: privateKey, padding: constants.RSA_PKCS1_PADDING },
      Buffer.from(encryptedKey, 'base64')
    ).toString('utf8');
    const responseAesKey = Buffer.from(keyBase64, 'base64');
    const decipher = createDecipheriv('aes-256-ecb', responseAesKey, null);
    decipher.setAutoPadding(true);
    text = Buffer.concat([decipher.update(Buffer.from(text, 'base64')), decipher.final()]).toString('utf8');
  }
  let body;
  try { body = JSON.parse(text); } catch { body = text; }
  return { status: response.status, body, headers: Object.fromEntries(response.headers) };
}

async function api(path, { method = 'GET', token, body, headers = {}, encrypted = false } = {}) {
  let payload = body;
  let aesKey;
  if (encrypted) {
    aesKey = randomBytes(16).toString('hex');
    const cipher = createCipheriv('aes-256-ecb', Buffer.from(aesKey), null);
    cipher.setAutoPadding(true);
    payload = Buffer.concat([cipher.update(JSON.stringify(body), 'utf8'), cipher.final()]).toString('base64');
    headers['encrypt-key'] = publicEncrypt(
      { key: publicKey, padding: constants.RSA_PKCS1_PADDING },
      Buffer.from(aesKey).toString('base64')
    ).toString('base64');
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
    headers.clientid = 'e5cd7e4891bf95d1d19206ce24a7b32e';
  }
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: { ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}), ...headers },
    body: body ? (body instanceof FormData ? body : encrypted ? payload : JSON.stringify(body)) : undefined
  });
  return parseResponse(response);
}

async function upload(token, filename, syllabusVersionId, mappings, requestId) {
  const bytes = await readFile(join(fixtures, filename));
  const form = new FormData();
  form.append('file', new Blob([bytes], { type: 'application/x-ndjson' }), filename);
  form.append('importType', 'knowledge_point');
  form.append('syllabusVersionId', syllabusVersionId);
  form.append('subjectMappings', JSON.stringify(mappings));
  form.append('templateVersion', 'knowledge_point/1.0');
  return api('/api/admin/imports', { method: 'POST', token, body: form, headers: { 'X-Request-Id': requestId } });
}

async function validateAndWait(token, id) {
  const accepted = await api(`/api/admin/imports/${id}/validate`, { method: 'POST', token });
  check(accepted.status === 200 && accepted.body.code === 200, `batch ${id} validate accepted`, accepted);
  let progress;
  for (let attempt = 0; attempt < 120; attempt++) {
    progress = await api(`/api/admin/imports/${id}/progress`, { token });
    if (['waiting_confirm', 'failed'].includes(progress.body?.data?.status)) break;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  check(['waiting_confirm', 'failed'].includes(progress.body?.data?.status), `batch ${id} reached terminal precheck state`, progress);
  return progress.body.data;
}

async function allIssues(token, id) {
  const rows = [];
  for (let page = 1; ; page++) {
    const response = await api(`/api/admin/imports/${id}/issues?pageNum=${page}&pageSize=100&orderByColumn=lineNo&isAsc=asc`, { token });
    check(response.status === 200 && response.body.code === 200, `batch ${id} issues page ${page}`, response);
    rows.push(...response.body.data.rows);
    if (rows.length >= response.body.data.total) return rows;
  }
}

async function main() {
  await mkdir(results, { recursive: true });

  const unauthorised = await api('/api/admin/imports/options?type=knowledge_point');
  check(unauthorised.status === 401 || unauthorised.body?.code === 401, 'options rejects unauthenticated request', unauthorised);

  const login = await api('/auth/login', {
    method: 'POST',
    encrypted: true,
    body: {
      username: process.env.CERTMUSE_TEST_USERNAME ?? 'admin',
      password: process.env.CERTMUSE_TEST_PASSWORD ?? 'admin123',
      clientId: 'e5cd7e4891bf95d1d19206ce24a7b32e',
      grantType: 'password'
    }
  });
  const token = login.body?.data?.access_token;
  check(login.status === 200 && login.body?.code === 200 && token, 'admin login succeeds', {
    status: login.status,
    code: login.body?.code,
    hasAccessToken: Boolean(token)
  });

  const options = await api('/api/admin/imports/options?type=knowledge_point', { token });
  check(options.status === 200 && options.body?.code === 200, 'options returns import context', options);
  const syllabus = options.body.data.syllabusVersions[0];
  const subjectsResponse = await api(`/api/admin/imports/options?type=knowledge_point&syllabusVersionId=${syllabus.id}`, { token });
  const subjects = subjectsResponse.body.data.examSubjects;
  check(subjects.length === 3, 'three subject mappings are available', subjects);
  const mappings = subjects.map(subject => ({ subjectNo: subject.subjectNo, examSubjectId: subject.id }));
  report.context = { syllabus, subjects };

  const unique = `${Date.now()}-${randomBytes(4).toString('hex')}`;
  const validCreate = await upload(token, 'knowledge-point-v7-valid.jsonl', syllabus.id, mappings, `jsonl-valid-${unique}`);
  check(validCreate.status === 200 && validCreate.body?.code === 200 && !validCreate.body.data.reused, 'valid baseline upload creates batch', validCreate);
  const validId = validCreate.body.data.id;
  report.batches.valid = { id: validId, create: validCreate.body.data };

  const reused = await upload(token, 'knowledge-point-v7-valid.jsonl', syllabus.id, mappings, `jsonl-valid-${unique}`);
  check(reused.status === 200 && reused.body.data.reused && reused.body.data.id === validId, 'identical request id reuses batch', reused);

  const conflict = await upload(token, 'knowledge-point-v7-valid-no-final-newline.jsonl', syllabus.id, mappings, `jsonl-valid-${unique}`);
  check(conflict.status === 409 && conflict.body?.data?.errorCode === 'IMPORT_REQUEST_ID_CONFLICT', 'changed payload with same request id conflicts', conflict);

  const beforeKnowledgeCount = Number(process.env.CERTMUSE_KNOWLEDGE_COUNT_BEFORE ?? 0);
  report.database = { beforeKnowledgeCount };
  report.batches.valid.progress = await validateAndWait(token, validId);
  check(report.batches.valid.progress.status === 'waiting_confirm', 'valid baseline waits for confirmation', report.batches.valid.progress);
  check(report.batches.valid.progress.totalCount === 1001 && report.batches.valid.progress.validCount === 1001 &&
    report.batches.valid.progress.failedCount === 0, 'valid baseline counts are exact', report.batches.valid.progress);
  report.batches.valid.issues = await allIssues(token, validId);
  check(report.batches.valid.issues.length === 0, 'valid baseline has no issues');

  const edgeCreate = await upload(token, 'knowledge-point-v7-with-edge-cases.jsonl', syllabus.id, mappings, `jsonl-edge-${unique}`);
  check(edgeCreate.status === 200 && edgeCreate.body?.code === 200, 'combined edge fixture uploads', edgeCreate);
  const edgeId = edgeCreate.body.data.id;
  report.batches.edge = { id: edgeId, create: edgeCreate.body.data };
  report.batches.edge.progress = await validateAndWait(token, edgeId);
  report.batches.edge.issues = await allIssues(token, edgeId);
  check(report.batches.edge.progress.failedCount > 0 && report.batches.edge.issues.length > 0, 'edge fixture produces validation failures', report.batches.edge.progress);

  const manifest = JSON.parse(await readFile(join(here, 'expected-cases.json'), 'utf8'));
  const expectedPairs = manifest.cases.flatMap(test => test.expectedIssueCodes
    .filter(code => code !== 'KP_SUBJECT_MAPPING_UNKNOWN')
    .map(code => `${test.lineNo}:${code}`));
  const actualPairs = new Set(report.batches.edge.issues.map(issue => `${issue.lineNo}:${issue.issueCode}`));
  const missing = expectedPairs.filter(pair => !actualPairs.has(pair));
  check(missing.length === 0, 'all declared primary edge issue codes are observed', { missing, expected: expectedPairs.length, actual: actualPairs.size });

  const mappingCreate = await upload(
    token,
    'knowledge-point-subject-mapping-unknown.jsonl',
    syllabus.id,
    [mappings.find(mapping => mapping.subjectNo === 1)],
    `jsonl-mapping-${unique}`
  );
  check(mappingCreate.status === 200, 'unmapped-subject fixture uploads', mappingCreate);
  const mappingId = mappingCreate.body.data.id;
  report.batches.mappingUnknown = { id: mappingId, progress: await validateAndWait(token, mappingId) };
  report.batches.mappingUnknown.issues = await allIssues(token, mappingId);
  check(
    report.batches.mappingUnknown.issues.some(issue => issue.issueCode === 'KP_SUBJECT_MAPPING_UNKNOWN'),
    'missing explicit subject mapping is reported',
    report.batches.mappingUnknown.issues
  );

  for (const [filename, expectedStatus] of [
    ['knowledge-point-v7-valid-crlf.jsonl', 200],
    ['knowledge-point-v7-valid-no-final-newline.jsonl', 200],
    ['knowledge-point-v7-valid-with-bom.jsonl', 400],
    ['knowledge-point-invalid-utf8.jsonl', 400]
  ]) {
    const response = await upload(token, filename, syllabus.id, mappings, `jsonl-format-${basename(filename)}-${unique}`);
    check(response.status === expectedStatus, `${filename} upload status`, response);
    report[`upload_${filename}`] = { status: response.status, body: response.body };
  }

  const missingProgress = await api('/api/admin/imports/999999999999999999/progress', { token });
  check(missingProgress.status === 404, 'unknown batch returns 404', missingProgress);

  report.finishedAt = new Date().toISOString();
  report.passed = report.assertions.every(assertion => assertion.passed);
  await writeFile(join(results, 'integration-result.json'), `${JSON.stringify(report, null, 2)}\n`);
  console.log(JSON.stringify({
    passed: report.passed,
    assertions: report.assertions.length,
    validBatchId: validId,
    edgeBatchId: edgeId,
    edgeIssues: report.batches.edge.issues.length
  }));
}

main().catch(async error => {
  report.finishedAt = new Date().toISOString();
  report.passed = false;
  report.error = { message: error.message, stack: error.stack };
  await mkdir(results, { recursive: true });
  await writeFile(join(results, 'integration-result.json'), `${JSON.stringify(report, null, 2)}\n`);
  console.error(error);
  process.exitCode = 1;
});
