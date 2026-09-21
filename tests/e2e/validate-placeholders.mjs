import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const manifest = JSON.parse(await readFile(new URL('./page-manifest.json', import.meta.url), 'utf8'));
const defects = manifest.pages.filter(entry => entry.kind === 'source-defect');

for (const defect of defects) {
  const source = await readFile(resolve(root, defect.source), 'utf8');
  if (!source.includes("EmptyBusinessPage") || !source.includes(`title=\"${defect.title}\"`)) {
    throw new Error(`${defect.id} no longer matches the tracked placeholder source: ${defect.source}`);
  }
}
console.log(JSON.stringify({ passed: true, expectedDefects: defects.length }));
