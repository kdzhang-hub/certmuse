export type QualificationLevel = 'LOW' | 'MIDDLE' | 'HIGH';
export type Direction = 'software' | 'infrastructure' | 'systems' | 'management' | 'digital';
export type WorkPreference = 'build' | 'operate' | 'analyse' | 'coordinate';

export interface QualificationProfile {
  code: string;
  name: string;
  level: QualificationLevel;
  nearestExamStartDate?: string | null;
  direction: Direction;
  secondaryDirections?: Direction[];
  preferences: WorkPreference[];
  specialties: string[];
  suitableFor: string;
  preparationNote: string;
}

export interface QualificationCatalogueItem {
  certificationCode: string;
  certificationName: string;
  qualificationLevel: QualificationLevel;
  nearestExamStartDate: string | null;
}

export interface ExamPeriod {
  id: string;
  label: string;
  availability: 'official' | 'planning';
  eligibleCodes: string[];
}

export interface FitAnswers {
  periodId: string;
  stage: string;
  foundation: string;
  direction: Direction;
  preference: WorkPreference;
  specialty: string;
}

const qualifications: QualificationProfile[] = [
  {
    code: 'SYSTEM_ANALYST',
    name: '系统分析师',
    level: 'HIGH',
    direction: 'systems',
    secondaryDirections: ['management'],
    preferences: ['analyse'],
    specialties: ['analysis', 'system'],
    suitableFor: '关注业务需求、系统分析和总体方案的人。',
    preparationNote: '需要较强的分析表达与系统化知识积累。'
  },
  {
    code: 'SYSTEM_ARCHITECT',
    name: '系统架构设计师',
    level: 'HIGH',
    direction: 'software',
    secondaryDirections: ['systems'],
    preferences: ['build', 'analyse'],
    specialties: ['architecture', 'software'],
    suitableFor: '希望承担复杂软件系统设计与技术方案工作的人。',
    preparationNote: '适合作为已有开发与设计基础后的进阶方向。'
  },
  {
    code: 'INFORMATION_SYSTEM_PROJECT_MANAGER',
    name: '信息系统项目管理师',
    level: 'HIGH',
    direction: 'management',
    secondaryDirections: ['systems'],
    preferences: ['coordinate', 'analyse'],
    specialties: ['project', 'system'],
    suitableFor: '关注信息化项目交付、沟通与管理的人。',
    preparationNote: '需要结合项目实践理解管理方法，证书不能替代项目经验。'
  },
  {
    code: 'NETWORK_PLANNING_DESIGNER',
    name: '网络规划设计师',
    level: 'HIGH',
    direction: 'infrastructure',
    preferences: ['analyse', 'build'],
    specialties: ['network', 'planning'],
    suitableFor: '希望从事网络规划、设计与整体方案工作的人。',
    preparationNote: '更适合已有网络技术基础后继续深入。'
  },
  {
    code: 'SYSTEM_PLANNING_MANAGER',
    name: '系统规划与管理师',
    level: 'HIGH',
    direction: 'management',
    secondaryDirections: ['systems'],
    preferences: ['coordinate', 'analyse'],
    specialties: ['planning', 'service'],
    suitableFor: '关注信息系统规划、治理与服务管理的人。',
    preparationNote: '需要理解组织、服务和技术之间的协同关系。'
  },
  {
    code: 'SOFTWARE_TESTER',
    name: '软件评测师',
    level: 'MIDDLE',
    direction: 'software',
    preferences: ['analyse'],
    specialties: ['testing', 'software'],
    suitableFor: '关注软件质量、测试设计和问题分析的人。',
    preparationNote: '应同时建立软件工程与测试方法基础。'
  },
  {
    code: 'SOFTWARE_DESIGNER',
    name: '软件设计师',
    level: 'MIDDLE',
    direction: 'software',
    preferences: ['build', 'analyse'],
    specialties: ['software', 'development'],
    suitableFor: '希望系统学习软件设计与开发基础的人。',
    preparationNote: '适合从编程和软件工程基础逐步建立能力。'
  },
  {
    code: 'SOFTWARE_PROCESS_ASSESSOR',
    name: '软件过程能力评估师',
    level: 'MIDDLE',
    direction: 'management',
    secondaryDirections: ['software'],
    preferences: ['analyse', 'coordinate'],
    specialties: ['process', 'software'],
    suitableFor: '关注软件过程改进、评估与质量管理的人。',
    preparationNote: '需要理解研发流程并结合组织实践。'
  },
  {
    code: 'NETWORK_ENGINEER',
    name: '网络工程师',
    level: 'MIDDLE',
    direction: 'infrastructure',
    preferences: ['build', 'operate'],
    specialties: ['network'],
    suitableFor: '希望从事网络建设、配置和维护工作的人。',
    preparationNote: '可从网络基础、协议和实际配置逐步准备。'
  },
  {
    code: 'MULTIMEDIA_APPLICATION_DESIGNER',
    name: '多媒体应用设计师',
    level: 'MIDDLE',
    direction: 'digital',
    preferences: ['build', 'analyse'],
    specialties: ['multimedia'],
    suitableFor: '关注多媒体内容、交互与应用设计的人。',
    preparationNote: '需要把内容表达与应用设计能力结合起来。'
  },
  {
    code: 'EMBEDDED_SYSTEM_DESIGNER',
    name: '嵌入式系统设计师',
    level: 'MIDDLE',
    direction: 'software',
    secondaryDirections: ['digital'],
    preferences: ['build'],
    specialties: ['embedded', 'hardware'],
    suitableFor: '希望面向软硬件结合场景做开发与设计的人。',
    preparationNote: '需要程序设计、硬件基础和系统知识的共同积累。'
  },
  {
    code: 'COMPUTER_AIDED_DESIGNER',
    name: '计算机辅助设计师',
    level: 'MIDDLE',
    direction: 'digital',
    preferences: ['build'],
    specialties: ['cad'],
    suitableFor: '关注计算机辅助设计工具与应用的人。',
    preparationNote: '适合将专业设计场景与数字工具结合准备。'
  },
  {
    code: 'ECOMMERCE_DESIGNER',
    name: '电子商务设计师',
    level: 'MIDDLE',
    direction: 'digital',
    secondaryDirections: ['software'],
    preferences: ['build', 'analyse'],
    specialties: ['ecommerce'],
    suitableFor: '关注电子商务系统、业务流程与应用设计的人。',
    preparationNote: '需要兼顾业务理解与技术实现。'
  },
  {
    code: 'SYSTEM_INTEGRATION_PROJECT_MANAGER',
    name: '系统集成项目管理工程师',
    level: 'MIDDLE',
    direction: 'management',
    secondaryDirections: ['systems'],
    preferences: ['coordinate', 'analyse'],
    specialties: ['project', 'integration'],
    suitableFor: '希望参与信息系统集成项目管理与交付的人。',
    preparationNote: '适合从项目过程、沟通和系统集成基础开始。'
  },
  {
    code: 'INFORMATION_SYSTEM_SUPERVISOR',
    name: '信息系统监理师',
    level: 'MIDDLE',
    direction: 'management',
    secondaryDirections: ['systems'],
    preferences: ['analyse', 'coordinate'],
    specialties: ['supervision', 'system'],
    suitableFor: '关注信息系统建设过程监督与质量控制的人。',
    preparationNote: '需要理解项目建设流程、规范与沟通边界。'
  },
  {
    code: 'INFORMATION_SECURITY_ENGINEER',
    name: '信息安全工程师',
    level: 'MIDDLE',
    direction: 'infrastructure',
    preferences: ['analyse', 'operate'],
    specialties: ['security'],
    suitableFor: '希望从事信息安全防护、风险分析和安全运营的人。',
    preparationNote: '应先建立网络、系统和安全基础，再逐步深入。'
  },
  {
    code: 'DATABASE_SYSTEM_ENGINEER',
    name: '数据库系统工程师',
    level: 'MIDDLE',
    direction: 'systems',
    secondaryDirections: ['software'],
    preferences: ['build', 'analyse'],
    specialties: ['database'],
    suitableFor: '关注数据建模、数据库设计和数据系统的人。',
    preparationNote: '需要扎实理解数据结构、数据库原理与实践。'
  },
  {
    code: 'INFORMATION_SYSTEM_MANAGER',
    name: '信息系统管理工程师',
    level: 'MIDDLE',
    direction: 'systems',
    preferences: ['operate', 'analyse'],
    specialties: ['operation', 'system'],
    suitableFor: '希望从事信息系统运行、维护和管理的人。',
    preparationNote: '可从系统运行、服务支持和基础管理逐步准备。'
  },
  {
    code: 'COMPUTER_HARDWARE_ENGINEER',
    name: '计算机硬件工程师',
    level: 'MIDDLE',
    direction: 'digital',
    preferences: ['build', 'operate'],
    specialties: ['hardware'],
    suitableFor: '关注计算机硬件、设备与相关技术支持的人。',
    preparationNote: '需要硬件原理与工程实践的基础。'
  },
  {
    code: 'INFORMATION_TECH_SUPPORT_ENGINEER',
    name: '信息技术支持工程师',
    level: 'MIDDLE',
    direction: 'infrastructure',
    secondaryDirections: ['systems'],
    preferences: ['operate'],
    specialties: ['support'],
    suitableFor: '希望从事技术支持、故障处理和服务保障的人。',
    preparationNote: '适合作为建立系统与网络支持能力的起点。'
  },
  {
    code: 'PROGRAMMER',
    name: '程序员',
    level: 'LOW',
    direction: 'software',
    preferences: ['build'],
    specialties: ['development'],
    suitableFor: '希望从编程与软件开发基础开始的人。',
    preparationNote: '适合先建立程序设计和计算机基础。'
  },
  {
    code: 'NETWORK_ADMINISTRATOR',
    name: '网络管理员',
    level: 'LOW',
    direction: 'infrastructure',
    preferences: ['operate'],
    specialties: ['network'],
    suitableFor: '希望从网络运行、配置和基础维护开始的人。',
    preparationNote: '适合先建立网络基础与日常运维能力。'
  },
  {
    code: 'MULTIMEDIA_APPLICATION_PRODUCER',
    name: '多媒体应用制作技术员',
    level: 'LOW',
    direction: 'digital',
    preferences: ['build'],
    specialties: ['multimedia'],
    suitableFor: '希望从多媒体内容制作与应用基础开始的人。',
    preparationNote: '适合先积累数字内容制作和工具使用能力。'
  },
  {
    code: 'ECOMMERCE_TECHNICIAN',
    name: '电子商务技术员',
    level: 'LOW',
    direction: 'digital',
    secondaryDirections: ['software'],
    preferences: ['build', 'operate'],
    specialties: ['ecommerce'],
    suitableFor: '希望从电子商务技术与应用支持开始的人。',
    preparationNote: '适合先建立电商场景、网站和基础技术知识。'
  },
  {
    code: 'INFORMATION_SYSTEM_OPERATION_ADMINISTRATOR',
    name: '信息系统运行管理员',
    level: 'LOW',
    direction: 'systems',
    preferences: ['operate'],
    specialties: ['operation', 'system'],
    suitableFor: '希望从信息系统日常运行与服务支持开始的人。',
    preparationNote: '适合先建立系统运行、流程和问题处理基础。'
  },
  {
    code: 'WEB_PAGE_PRODUCER',
    name: '网页制作员',
    level: 'LOW',
    direction: 'software',
    secondaryDirections: ['digital'],
    preferences: ['build'],
    specialties: ['web'],
    suitableFor: '希望从网页制作与基础前端实现开始的人。',
    preparationNote: '适合先建立网页结构、样式与交互基础。'
  },
  {
    code: 'INFORMATION_PROCESSING_TECHNICIAN',
    name: '信息处理技术员',
    level: 'LOW',
    direction: 'digital',
    secondaryDirections: ['systems'],
    preferences: ['operate'],
    specialties: ['information-processing'],
    suitableFor: '希望从信息处理、办公应用与数据整理开始的人。',
    preparationNote: '适合先建立规范处理信息与常用工具应用能力。'
  }
];

const spring2026 = [
  'SYSTEM_ANALYST',
  'SYSTEM_ARCHITECT',
  'INFORMATION_SYSTEM_PROJECT_MANAGER',
  'SOFTWARE_DESIGNER',
  'NETWORK_ENGINEER',
  'SOFTWARE_TESTER',
  'ECOMMERCE_DESIGNER',
  'EMBEDDED_SYSTEM_DESIGNER',
  'DATABASE_SYSTEM_ENGINEER',
  'INFORMATION_SYSTEM_MANAGER',
  'SYSTEM_INTEGRATION_PROJECT_MANAGER',
  'INFORMATION_PROCESSING_TECHNICIAN',
  'INFORMATION_SYSTEM_OPERATION_ADMINISTRATOR'
];
const autumn2026 = [
  'SYSTEM_ANALYST',
  'SYSTEM_ARCHITECT',
  'NETWORK_PLANNING_DESIGNER',
  'SYSTEM_PLANNING_MANAGER',
  'SOFTWARE_DESIGNER',
  'NETWORK_ENGINEER',
  'INFORMATION_SECURITY_ENGINEER',
  'INFORMATION_SYSTEM_MANAGER',
  'MULTIMEDIA_APPLICATION_DESIGNER',
  'SYSTEM_INTEGRATION_PROJECT_MANAGER',
  'PROGRAMMER',
  'NETWORK_ADMINISTRATOR',
  'INFORMATION_PROCESSING_TECHNICIAN'
];

// 2027 年及以后是可维护的备考规划配置，正式安排发布后以当期官方名单替换。
export const examPeriods: ExamPeriod[] = [
  { id: '2026-h2', label: '2026 年下半年', availability: 'official', eligibleCodes: autumn2026 },
  { id: '2027-h1', label: '2027 年上半年', availability: 'planning', eligibleCodes: spring2026 },
  { id: '2027-h2', label: '2027 年下半年', availability: 'planning', eligibleCodes: autumn2026 },
  { id: '2028-h1', label: '2028 年上半年', availability: 'planning', eligibleCodes: spring2026 },
  { id: '2028-h2', label: '2028 年下半年', availability: 'planning', eligibleCodes: autumn2026 },
  { id: '2029-h1', label: '2029 年上半年', availability: 'planning', eligibleCodes: spring2026 },
  { id: '2029-h2', label: '2029 年下半年', availability: 'planning', eligibleCodes: autumn2026 },
  { id: '2030-h1', label: '2030 年上半年', availability: 'planning', eligibleCodes: spring2026 }
];

export const directionLabels: Record<Direction, string> = {
  software: '软件开发与设计',
  infrastructure: '网络、系统与安全保障',
  systems: '信息系统建设与运行',
  management: '项目、治理与服务管理',
  digital: '数字内容、应用与技术支持'
};

export const qualificationLevelLabels: Record<QualificationLevel, string> = {
  LOW: '初级资格',
  MIDDLE: '中级资格',
  HIGH: '高级资格'
};

export const workPreferenceLabels: Record<WorkPreference, string> = {
  build: '技术实现与设计',
  operate: '运行维护与保障',
  analyse: '分析与方案设计',
  coordinate: '项目与协同管理'
};

export const specialtyLabels: Record<string, string> = {
  analysis: '需求与系统分析',
  architecture: '软件架构与方案设计',
  cad: '计算机辅助设计',
  database: '数据与数据库系统',
  development: '程序设计与应用开发',
  ecommerce: '电子商务技术与应用',
  embedded: '嵌入式与软硬件结合',
  hardware: '计算机硬件与设备',
  information: '信息技术应用',
  'information-processing': '信息处理与应用支持',
  integration: '系统集成与交付',
  multimedia: '多媒体内容与应用制作',
  network: '网络建设与运行',
  operation: '信息系统运行与管理',
  planning: '规划、治理与服务管理',
  process: '软件过程与质量改进',
  project: '信息化项目管理',
  security: '信息安全',
  service: '信息技术服务',
  software: '软件工程与开发',
  support: '技术支持与故障处理',
  supervision: '信息系统监理',
  system: '信息系统建设与集成',
  testing: '软件测试与质量分析',
  web: '网页与前端制作'
};

export function qualificationByCode(code: string | undefined) {
  return qualifications.find(item => item.code === code);
}

export function eligibleQualifications(periodId: string, profiles = qualifications) {
  const period = examPeriods.find(item => item.id === periodId) ?? examPeriods[0];
  return profiles.filter(item => period.eligibleCodes.includes(item.code));
}

export function recommendQualification(answers: FitAnswers, candidates = eligibleQualifications(answers.periodId)) {
  return candidates.reduce(
    (best, candidate) => (score(candidate, answers) > score(best, answers) ? candidate : best),
    candidates[0]
  );
}

export function mergeQualificationCatalogue(items: QualificationCatalogueItem[]): QualificationProfile[] {
  const profilesByCode = new Map(qualifications.map(item => [item.code, item]));
  return items.flatMap(item => {
    const profile = profilesByCode.get(item.certificationCode);
    return profile
      ? [
          {
            ...profile,
            name: item.certificationName,
            level: item.qualificationLevel,
            nearestExamStartDate: item.nearestExamStartDate
          }
        ]
      : [];
  });
}

export function formatNextOfficialExamDate(date: string | null | undefined) {
  if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return '下一场官方考试：暂未公布';
  const [year, month, day] = date.split('-').map(Number);
  return `下一场官方考试：${year}年${month}月${day}日`;
}

function score(candidate: QualificationProfile, answers: FitAnswers) {
  let total =
    candidate.direction === answers.direction ? 20 : candidate.secondaryDirections?.includes(answers.direction) ? 8 : 0;
  if (candidate.preferences.includes(answers.preference)) total += 9;
  if (candidate.specialties.includes(answers.specialty)) total += 18;

  if (answers.foundation === 'none') total += candidate.level === 'LOW' ? 6 : candidate.level === 'MIDDLE' ? 3 : 0;
  if (answers.foundation === 'coursework')
    total += candidate.level === 'MIDDLE' ? 5 : candidate.level === 'LOW' ? 2 : 1;
  if (answers.foundation === 'technical')
    total += candidate.level === 'MIDDLE' ? 5 : candidate.level === 'HIGH' ? 3 : 1;
  if (answers.foundation === 'management')
    total +=
      candidate.direction === 'management' && candidate.level === 'HIGH'
        ? 6
        : candidate.direction === 'management'
          ? 4
          : 1;

  if (answers.stage === 'technical') total += candidate.level === 'MIDDLE' ? 3 : candidate.level === 'HIGH' ? 2 : 0;
  if (answers.stage === 'management')
    total += candidate.direction === 'management' ? 4 : candidate.level === 'HIGH' ? 2 : 0;
  if (answers.stage === 'career-change' || answers.stage.startsWith('year-'))
    total += candidate.level === 'LOW' ? 3 : candidate.level === 'MIDDLE' ? 1 : 0;
  return total;
}

export { qualifications };
