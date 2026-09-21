import { readFile } from 'node:fs/promises';

const manifestPath = new URL('./page-manifest.json', import.meta.url);
const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
const allowedKinds = new Set(['public', 'student-guest', 'admin', 'legacy-redirect', 'expected-defect', 'source-defect']);
const ids = new Set();

if (!Array.isArray(manifest.pages) || manifest.pages.length === 0) {
  throw new Error('page-manifest.json must contain at least one page case.');
}
for (const entry of manifest.pages) {
  if (!/^([A-Z]+)-\d{3}$/.test(entry.id ?? '')) throw new Error(`Invalid test id: ${entry.id}`);
  if (ids.has(entry.id)) throw new Error(`Duplicate test id: ${entry.id}`);
  if (!allowedKinds.has(entry.kind)) throw new Error(`Unsupported kind for ${entry.id}: ${entry.kind}`);
  if (entry.kind === 'expected-defect') {
    if (!entry.menuText || !entry.text || !entry.defectId) throw new Error(`Expected defect ${entry.id} lacks evidence metadata.`);
  } else if (entry.kind === 'source-defect') {
    if (!entry.source || !entry.title || !entry.defectId) throw new Error(`Source defect ${entry.id} lacks evidence metadata.`);
  } else if (entry.kind === 'legacy-redirect') {
    if (!entry.path?.startsWith('/') || !entry.target?.startsWith('/') || !entry.text) {
      throw new Error(`Legacy redirect ${entry.id} lacks route evidence metadata.`);
    }
  } else if (!entry.path?.startsWith('/')) {
    throw new Error(`Route case ${entry.id} must have an absolute path.`);
  }
  ids.add(entry.id);
}
for (const prefix of ['PUB-', 'AUTH-', 'STU-', 'ADM-', 'DEF-']) {
  if (![...ids].some(id => id.startsWith(prefix))) throw new Error(`Manifest is missing ${prefix} coverage.`);
}
console.log(JSON.stringify({ passed: true, cases: manifest.pages.length }));
