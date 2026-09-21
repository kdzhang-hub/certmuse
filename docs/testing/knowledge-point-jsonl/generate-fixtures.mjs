import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../../..');
const sourcePath = resolve(repo, 'target/knowledge_point_v7.jsonl');
const fixturesDir = resolve(here, 'fixtures');
const source = await readFile(sourcePath, 'utf8');
const normalLines = source.replace(/\r?\n$/, '').split(/\r?\n/);

if (normalLines.length !== 1001) {
  throw new Error(`Expected 1001 baseline records, got ${normalLines.length}`);
}

await mkdir(fixturesDir, { recursive: true });
await writeFile(resolve(fixturesDir, 'knowledge-point-v7-valid.jsonl'), `${normalLines.join('\n')}\n`, 'utf8');

const cases = [];
let sequence = 900000;

function valid(overrides = {}) {
  sequence += 1;
  const number = `1.${sequence}`;
  return {
    schema_version: '1.0',
    source_key: `knowledge-tree-v7:1:${number}`,
    subject_no: 1,
    syllabus_number: number,
    syllabus_title: `边界案例 ${sequence}`,
    parent_syllabus_number: null,
    tree_depth: 1,
    sort_order: sequence,
    description: null,
    importance: null,
    diagnostic_enabled: false,
    recommendation_enabled: false,
    status: '0',
    ...overrides
  };
}

function objectCase(id, expectedCodes, overrides, note) {
  cases.push({ id, expectedCodes, raw: JSON.stringify(valid(overrides)), note });
}

function rawCase(id, expectedCodes, raw, note) {
  cases.push({ id, expectedCodes, raw, note });
}

objectCase('valid-unicode-title', [], { syllabus_title: '中文 English （）—，。🚀' }, '合法Unicode与Emoji标题');
objectCase('valid-title-length-500', [], { syllabus_title: '界'.repeat(500) }, '标题长度上边界500');
objectCase('schema-number', ['KP_FIELD_TYPE_INVALID'], { schema_version: 1 }, 'schema_version错误类型');
objectCase('schema-unsupported', ['KP_SCHEMA_VERSION_UNSUPPORTED'], { schema_version: '2.0' }, '不支持的Schema');
objectCase('missing-required-field', ['KP_REQUIRED_FIELD_MISSING'], (() => {
  const row = valid();
  delete row.status;
  return row;
})(), '缺少必填status；该case使用完整对象覆盖，因此另由下方专用raw修正');
cases.pop();
{
  const row = valid();
  delete row.status;
  rawCase('missing-required-field', ['KP_REQUIRED_FIELD_MISSING'], JSON.stringify(row), '缺少必填status');
}
objectCase('unknown-field', ['KP_UNKNOWN_FIELD'], { unexpected_field: true }, '严格白名单未知字段');
objectCase('forbidden-server-id', ['KP_UNKNOWN_FIELD'], { id: 123 }, '禁止上传服务端id');
objectCase('source-key-empty', ['KP_SOURCE_KEY_INVALID'], { source_key: '' }, 'source_key空字符串');
objectCase('source-key-leading-space', ['KP_SOURCE_KEY_INVALID'], { source_key: ' knowledge-tree-v7:1:1.900001' }, 'source_key首部空格');
objectCase('source-key-wrong-prefix', ['KP_SOURCE_KEY_INVALID'], { source_key: 'knowledge-tree-v6:1:1.900002' }, '来源版本错误');
objectCase('source-key-subject-mismatch', ['KP_SOURCE_KEY_INVALID'], { source_key: 'knowledge-tree-v7:2:1.900003' }, 'source_key科目不匹配');
objectCase('source-key-number-mismatch', ['KP_SOURCE_KEY_INVALID'], { source_key: 'knowledge-tree-v7:1:1.999999' }, 'source_key编号不匹配');
objectCase('subject-zero', ['KP_SUBJECT_NO_INVALID'], { subject_no: 0 }, 'subject_no下边界外');
objectCase('subject-four', ['KP_SUBJECT_NO_INVALID'], { subject_no: 4 }, 'subject_no上边界外');
objectCase('subject-string', ['KP_FIELD_TYPE_INVALID'], { subject_no: '1' }, 'subject_no错误类型');
objectCase('subject-mapping-unknown', ['KP_SUBJECT_MAPPING_UNKNOWN'], {
  subject_no: 2,
  syllabus_number: '2.900010',
  source_key: 'knowledge-tree-v7:2:2.900010'
}, '仅提交科目1映射时科目2无显式映射');
objectCase('number-empty', ['KP_SYLLABUS_NUMBER_INVALID'], { syllabus_number: '' }, '编号空字符串');
objectCase('number-leading-space', ['KP_SYLLABUS_NUMBER_INVALID'], { syllabus_number: ' 1.900011' }, '编号首部空格');
objectCase('number-leading-zero-segment', ['KP_SYLLABUS_NUMBER_INVALID'], { syllabus_number: '1.01' }, '编号分段补零');
objectCase('number-zero-segment', ['KP_SYLLABUS_NUMBER_INVALID'], { syllabus_number: '1.0' }, '编号分段为0');
objectCase('number-subject-mismatch', ['KP_SUBJECT_NUMBER_MISMATCH'], { syllabus_number: '2.900012' }, '编号首段与subject_no不一致');
objectCase('number-too-long', ['KP_FIELD_VALUE_INVALID'], { syllabus_number: `1.${'9'.repeat(99)}` }, '编号超过100字符');
objectCase('number-wrong-type', ['KP_FIELD_TYPE_INVALID'], { syllabus_number: 1.1 }, '编号错误类型');
objectCase('title-empty', ['KP_FIELD_VALUE_INVALID'], { syllabus_title: '' }, '标题空字符串');
objectCase('title-whitespace', ['KP_FIELD_VALUE_INVALID'], { syllabus_title: '   ' }, '标题全空白');
objectCase('title-length-501', ['KP_FIELD_VALUE_INVALID'], { syllabus_title: '界'.repeat(501) }, '标题超过500字符');
objectCase('title-wrong-type', ['KP_FIELD_TYPE_INVALID'], { syllabus_title: 123 }, '标题错误类型');
objectCase('parent-wrong-type', ['KP_FIELD_TYPE_INVALID'], { parent_syllabus_number: 1.1 }, '父编号错误类型');
objectCase('parent-whitespace', ['KP_SYLLABUS_NUMBER_INVALID'], { parent_syllabus_number: ' 1.1' }, '父编号首部空格');
objectCase('parent-missing', ['KP_PARENT_NOT_FOUND'], { parent_syllabus_number: '1.987654', tree_depth: 2 }, '父节点不存在');
objectCase('parent-self', ['KP_TREE_CYCLE'], (() => {
  const row = valid();
  return { parent_syllabus_number: row.syllabus_number, tree_depth: 2 };
})(), '自引用；最终编号由对象生成器决定，另由专用raw修正');
cases.pop();
{
  const row = valid();
  row.parent_syllabus_number = row.syllabus_number;
  row.tree_depth = 2;
  rawCase('parent-self', ['KP_TREE_CYCLE'], JSON.stringify(row), '父节点自引用');
}
objectCase('depth-zero', ['KP_FIELD_VALUE_INVALID'], { tree_depth: 0 }, 'tree_depth下边界外');
objectCase('depth-negative', ['KP_FIELD_VALUE_INVALID'], { tree_depth: -1 }, 'tree_depth负数');
objectCase('depth-32768', ['KP_FIELD_VALUE_INVALID'], { tree_depth: 32768 }, 'tree_depth超过smallint');
objectCase('depth-string', ['KP_FIELD_TYPE_INVALID'], { tree_depth: '1' }, 'tree_depth错误类型');
objectCase('root-depth-two', ['KP_TREE_DEPTH_INVALID'], { tree_depth: 2 }, '根节点层级不是1');
objectCase('sort-zero', ['KP_FIELD_VALUE_INVALID'], { sort_order: 0 }, 'sort_order下边界外');
objectCase('sort-negative', ['KP_FIELD_VALUE_INVALID'], { sort_order: -1 }, 'sort_order负数');
objectCase('sort-over-int', ['KP_FIELD_VALUE_INVALID'], { sort_order: 2147483648 }, 'sort_order超过integer');
objectCase('sort-string', ['KP_FIELD_TYPE_INVALID'], { sort_order: '1' }, 'sort_order错误类型');
objectCase('description-empty', ['KP_FIELD_VALUE_INVALID'], { description: '' }, 'V1 description必须为null');
objectCase('description-wrong-type', ['KP_FIELD_TYPE_INVALID'], { description: 1 }, 'description错误类型');
objectCase('importance-one', ['KP_FIELD_VALUE_INVALID'], { importance: 1 }, 'V1 importance固定为null');
objectCase('importance-four', ['KP_FIELD_VALUE_INVALID'], { importance: 4 }, 'importance非法枚举');
objectCase('diagnostic-true', ['KP_FIELD_VALUE_INVALID'], { diagnostic_enabled: true }, 'V1 diagnostic_enabled固定false');
objectCase('diagnostic-string', ['KP_FIELD_TYPE_INVALID'], { diagnostic_enabled: 'false' }, '诊断开关错误类型');
objectCase('recommendation-true', ['KP_FIELD_VALUE_INVALID'], { recommendation_enabled: true }, 'V1 recommendation_enabled固定false');
objectCase('recommendation-number', ['KP_FIELD_TYPE_INVALID'], { recommendation_enabled: 0 }, '推荐开关错误类型');
objectCase('status-disabled', ['KP_FIELD_VALUE_INVALID'], { status: '1' }, 'V1 status固定0');
objectCase('status-number', ['KP_FIELD_TYPE_INVALID'], { status: 0 }, 'status错误类型');
objectCase('status-unknown', ['KP_FIELD_VALUE_INVALID'], { status: '9' }, 'status非法值');

{
  const first = valid();
  const second = valid({
    source_key: first.source_key,
    subject_no: first.subject_no,
    syllabus_number: first.syllabus_number
  });
  rawCase('duplicate-source-and-number-first', ['KP_DUPLICATE_SOURCE_KEY', 'KP_DUPLICATE_SYLLABUS_NUMBER'], JSON.stringify(first), '重复组合第一条');
  rawCase('duplicate-source-and-number-second', ['KP_DUPLICATE_SOURCE_KEY', 'KP_DUPLICATE_SYLLABUS_NUMBER'], JSON.stringify(second), '重复组合第二条');
}

{
  const child = valid({ parent_syllabus_number: '1.990001', tree_depth: 2 });
  const parent = valid({
    source_key: 'knowledge-tree-v7:1:1.990001',
    syllabus_number: '1.990001',
    tree_depth: 1
  });
  rawCase('parent-after-child', ['KP_PARENT_ORDER_INVALID'], JSON.stringify(child), '父节点晚于子节点');
  rawCase('late-parent', [], JSON.stringify(parent), '供上一条验证顺序');
}

{
  const parent = valid({
    source_key: 'knowledge-tree-v7:1:1.990002',
    syllabus_number: '1.990002',
    tree_depth: 1,
    syllabus_title: ''
  });
  const child = valid({ parent_syllabus_number: '1.990002', tree_depth: 2 });
  rawCase('parent-nonstructural-error', ['KP_FIELD_VALUE_INVALID'], JSON.stringify(parent), '父记录只有非结构字段错误');
  rawCase('child-of-nonstructural-error', [], JSON.stringify(child), '非结构错误父节点仍可用于结构判断');
}

{
  const a = valid({
    source_key: 'knowledge-tree-v7:1:1.990003',
    syllabus_number: '1.990003',
    parent_syllabus_number: '1.990004',
    tree_depth: 2
  });
  const b = valid({
    source_key: 'knowledge-tree-v7:1:1.990004',
    syllabus_number: '1.990004',
    parent_syllabus_number: '1.990003',
    tree_depth: 2
  });
  rawCase('cycle-a', ['KP_TREE_CYCLE'], JSON.stringify(a), '循环A');
  rawCase('cycle-b', ['KP_TREE_CYCLE'], JSON.stringify(b), '循环B');
}

rawCase('invalid-json-syntax', ['KP_JSON_INVALID'], '{not valid json}', 'JSON语法错误');
rawCase('json-array-root', ['KP_JSON_INVALID'], '[1,2,3]', '根节点为数组');
rawCase('json-string-root', ['KP_JSON_INVALID'], '"text"', '根节点为字符串');
rawCase('json-number-root', ['KP_JSON_INVALID'], '123', '根节点为数字');
rawCase('json-boolean-root', ['KP_JSON_INVALID'], 'true', '根节点为布尔值');
rawCase('json-null-root', ['KP_JSON_INVALID'], 'null', '根节点为null');
rawCase('json-empty-line', ['KP_JSON_INVALID'], '', '纯空白/空物理行');
rawCase(
  'duplicate-json-key',
  ['KP_JSON_DUPLICATE_KEY'],
  '{"schema_version":"1.0","source_key":"first","source_key":"second"}',
  '同一对象重复字段名'
);

const largeBase = JSON.stringify(valid({ syllabus_title: '__TITLE__' }));
const markerBytes = Buffer.byteLength(largeBase.replace('__TITLE__', ''), 'utf8');
const oversizedTitle = 'a'.repeat(1_048_577 - markerBytes);
rawCase(
  'line-over-1mib',
  ['KP_JSON_LINE_TOO_LARGE'],
  largeBase.replace('__TITLE__', oversizedTitle),
  '物理行恰好1,048,577字节'
);

const combinedLines = [...normalLines, ...cases.map(testCase => testCase.raw)];
await writeFile(resolve(fixturesDir, 'knowledge-point-v7-with-edge-cases.jsonl'), combinedLines.join('\n'), 'utf8');
await writeFile(
  resolve(fixturesDir, 'knowledge-point-subject-mapping-unknown.jsonl'),
  `${cases.find(testCase => testCase.id === 'subject-mapping-unknown').raw}\n`,
  'utf8'
);
await writeFile(resolve(fixturesDir, 'knowledge-point-v7-valid-crlf.jsonl'), `${normalLines.join('\r\n')}\r\n`, 'utf8');
await writeFile(resolve(fixturesDir, 'knowledge-point-v7-valid-no-final-newline.jsonl'), normalLines.join('\n'), 'utf8');
await writeFile(
  resolve(fixturesDir, 'knowledge-point-v7-valid-with-bom.jsonl'),
  Buffer.concat([Buffer.from([0xef, 0xbb, 0xbf]), Buffer.from(`${normalLines[0]}\n`, 'utf8')])
);
await writeFile(
  resolve(fixturesDir, 'knowledge-point-invalid-utf8.jsonl'),
  Buffer.concat([Buffer.from(normalLines[0], 'utf8'), Buffer.from([0xc3, 0x28]), Buffer.from('\n')])
);

const manifest = {
  schemaVersion: 'knowledge-point-jsonl-test-manifest/1.0',
  generatedAt: new Date().toISOString(),
  source: 'target/knowledge_point_v7.jsonl',
  baselineLineCount: normalLines.length,
  combinedLineCount: combinedLines.length,
  appendedCaseCount: cases.length,
  baselineSha256: createHash('sha256').update(`${normalLines.join('\n')}\n`).digest('hex'),
  subjectMappingForIntegration: [
    { subjectNo: 1, examSubjectId: '<replace-with-seeded-subject-id-1>' },
    { subjectNo: 2, examSubjectId: '<replace-with-seeded-subject-id-2>' },
    { subjectNo: 3, examSubjectId: '<replace-with-seeded-subject-id-3>' }
  ],
  cases: cases.map((testCase, index) => ({
    id: testCase.id,
    lineNo: normalLines.length + index + 1,
    expectedIssueCodes: testCase.expectedCodes,
    note: testCase.note
  })),
  fileLevelCases: [
    { file: 'knowledge-point-v7-valid.jsonl', expected: 'accepted' },
    { file: 'knowledge-point-v7-valid-crlf.jsonl', expected: 'accepted' },
    { file: 'knowledge-point-v7-valid-no-final-newline.jsonl', expected: 'accepted' },
    { file: 'knowledge-point-v7-valid-with-bom.jsonl', expectedErrorCode: 'IMPORT_FILE_INVALID' },
    { file: 'knowledge-point-invalid-utf8.jsonl', expectedErrorCode: 'IMPORT_FILE_INVALID' }
  ]
};
await writeFile(resolve(here, 'expected-cases.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

console.log(
  JSON.stringify({
    baselineLines: normalLines.length,
    appendedCases: cases.length,
    combinedLines: combinedLines.length,
    combinedBytes: Buffer.byteLength(combinedLines.join('\n'), 'utf8')
  })
);
