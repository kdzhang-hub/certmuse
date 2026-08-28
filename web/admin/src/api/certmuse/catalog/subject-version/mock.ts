import type {
  QualificationForm,
  QualificationPageQuery,
  QualificationVO,
  SyllabusVersionForm,
  SyllabusVersionVO
} from './types';

type MockSyllabusVersionForm = SyllabusVersionForm & { certificationId: string };

const delay = (ms = 80) => new Promise(resolve => setTimeout(resolve, ms));
const now = '2026-08-04T09:30:00+08:00';
let sequence = 100;

const qualifications: QualificationVO[] = [
  qualification('900000000000000001', 'SYSTEM_ARCHITECT', '系统架构设计师', 'HIGH', '0', 1, 18, [
    version('900000000000000021', '900000000000000001', '第二版', '2024-05-01', 12),
    version('900000000000000022', '900000000000000001', '第一版', '2020-06-01', 4)
  ]),
  qualification('900000000000000002', 'SOFTWARE_DESIGNER', '软件设计师', 'MIDDLE', '0', 2, 0, [
    version('900000000000000023', '900000000000000002', '第六版', '2025-01-15', 0)
  ]),
  qualification('900000000000000003', 'NETWORK_ENGINEER', '网络工程师', 'MIDDLE', '1', 3, 0, [])
];

export async function listMockQualifications(query: QualificationPageQuery = {}): Promise<QualificationVO[]> {
  await delay();
  const keyword = query.keyword?.trim().toLowerCase();
  return qualifications
    .filter(item => {
      const searchable = `${item.certificationName} ${item.certificationCode} ${item.versions.map(v => v.versionName).join(' ')}`.toLowerCase();
      return (!keyword || searchable.includes(keyword))
        && (!query.qualificationLevel || item.qualificationLevel === query.qualificationLevel)
        && (!query.status || item.status === query.status);
    })
    .toSorted((left, right) => left.sortOrder - right.sortOrder || left.certificationName.localeCompare(right.certificationName, 'zh-CN'))
    .map(cloneQualification);
}

export async function createMockQualification(form: QualificationForm): Promise<QualificationVO> {
  await delay();
  validateQualification(form);
  if (qualifications.some(item => item.certificationCode.toLowerCase() === form.certificationCode.trim().toLowerCase())) {
    throw new Error('资格编码已存在，请更换后重试');
  }
  const item = qualification(nextId(), form.certificationCode.trim(), form.certificationName.trim(), form.qualificationLevel, form.status, form.sortOrder, 0, []);
  qualifications.push(item);
  return cloneQualification(item);
}

export async function updateMockQualification(id: string, form: QualificationForm): Promise<QualificationVO> {
  await delay();
  validateQualification(form);
  const item = requireQualification(id);
  if (qualifications.some(other => other.id !== id && other.certificationCode.toLowerCase() === form.certificationCode.trim().toLowerCase())) {
    throw new Error('资格编码已存在，请更换后重试');
  }
  Object.assign(item, { ...form, certificationCode: form.certificationCode.trim(), certificationName: form.certificationName.trim(), updateTime: new Date().toISOString() });
  return cloneQualification(item);
}

export async function deleteMockQualification(id: string): Promise<void> {
  await delay();
  const item = requireQualification(id);
  if (item.referenceCount) throw new Error(`该资格已被 ${item.referenceCount} 条业务数据引用，不能删除`);
  const referencedVersions = item.versions.filter(versionItem => versionItem.referenceCount > 0);
  if (referencedVersions.length) {
    throw new Error(`该资格下有 ${referencedVersions.length} 个版本仍被业务数据引用，不能删除`);
  }
  qualifications.splice(qualifications.indexOf(item), 1);
}

export async function createMockSyllabusVersion(form: MockSyllabusVersionForm): Promise<SyllabusVersionVO> {
  await delay();
  validateVersion(form);
  const qualificationItem = requireQualification(form.certificationId);
  ensureVersionUnique(qualificationItem, form.versionName);
  const item = version(nextId(), form.certificationId, form.versionName.trim(), form.publishedDate, 0);
  qualificationItem.versions.unshift(item);
  touch(qualificationItem);
  return { ...item };
}

export async function updateMockSyllabusVersion(id: string, form: MockSyllabusVersionForm): Promise<SyllabusVersionVO> {
  await delay();
  validateVersion(form);
  const qualificationItem = requireQualification(form.certificationId);
  const item = qualificationItem.versions.find(current => current.id === id);
  if (!item) throw new Error('版本不存在或已删除');
  ensureVersionUnique(qualificationItem, form.versionName, id);
  Object.assign(item, { versionName: form.versionName.trim(), publishedDate: form.publishedDate, updateTime: new Date().toISOString() });
  touch(qualificationItem);
  return { ...item };
}

export async function deleteMockSyllabusVersion(id: string): Promise<void> {
  await delay();
  const qualificationItem = qualifications.find(item => item.versions.some(current => current.id === id));
  if (!qualificationItem) throw new Error('版本不存在或已删除');
  const item = qualificationItem.versions.find(current => current.id === id)!;
  if (item.referenceCount) throw new Error(`该版本已被 ${item.referenceCount} 条业务数据引用，不能删除`);
  qualificationItem.versions.splice(qualificationItem.versions.indexOf(item), 1);
  touch(qualificationItem);
}

function qualification(id: string, certificationCode: string, certificationName: string, qualificationLevel: QualificationVO['qualificationLevel'], status: QualificationVO['status'], sortOrder: number, referenceCount: number, versions: SyllabusVersionVO[]): QualificationVO {
  return { id, certificationCode, certificationName, qualificationLevel, status, sortOrder, versionCount: versions.length, referenceCount, createTime: now, updateTime: now, versions };
}

function version(id: string, certificationId: string, versionName: string, publishedDate: string | null, referenceCount: number): SyllabusVersionVO {
  return { id, certificationId, versionName, publishedDate, referenceCount, createTime: now, updateTime: now };
}

function validateQualification(form: QualificationForm) {
  if (!form.certificationCode.trim()) throw new Error('请输入资格编码');
  if (!form.certificationName.trim()) throw new Error('请输入资格名称');
  if (!Number.isInteger(form.sortOrder) || form.sortOrder < 0) throw new Error('排序值必须为非负整数');
}

function validateVersion(form: MockSyllabusVersionForm) {
  if (!form.certificationId) throw new Error('请选择所属资格');
  if (!form.versionName.trim()) throw new Error('请输入版本名称');
}

function ensureVersionUnique(item: QualificationVO, name: string, ignoredId?: string) {
  if (item.versions.some(versionItem => versionItem.id !== ignoredId && versionItem.versionName.toLowerCase() === name.trim().toLowerCase())) {
    throw new Error('该资格下已存在同名版本');
  }
}

function requireQualification(id: string) {
  const item = qualifications.find(current => current.id === id);
  if (!item) throw new Error('资格不存在或已删除');
  return item;
}

function touch(item: QualificationVO) {
  item.versionCount = item.versions.length;
  item.updateTime = new Date().toISOString();
}

function cloneQualification(item: QualificationVO): QualificationVO {
  return { ...item, versions: item.versions.map(versionItem => ({ ...versionItem })) };
}

function nextId() {
  sequence += 1;
  return `990000000000000${sequence}`;
}
