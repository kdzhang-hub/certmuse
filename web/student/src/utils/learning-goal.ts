import type { ContractErrorVo, CreateLearningGoalRequest, LearningGoalOptionsVo } from '@/api/certmuse/learning/goals';

export interface RequestIdentity {
  fingerprint: string;
  id: string;
}

export function goalPayloadFingerprint(payload: CreateLearningGoalRequest): string {
  return JSON.stringify(payload);
}

export function requestIdentityFor(
  payload: CreateLearningGoalRequest,
  current?: RequestIdentity,
  uuid: () => string = () => crypto.randomUUID()
): RequestIdentity {
  const fingerprint = goalPayloadFingerprint(payload);
  return current?.fingerprint === fingerprint ? current : { fingerprint, id: uuid() };
}

export function clampDailyMinutes(value: number | undefined, options: LearningGoalOptionsVo): number {
  const { min, max, defaultValue } = options.dailyMinutes;
  if (value === undefined || Number.isNaN(value)) return defaultValue;
  return Math.min(max, Math.max(min, Math.round(value)));
}

export function contractErrorFrom(error: unknown): ContractErrorVo | undefined {
  const candidate = error as { responseData?: ContractErrorVo; response?: { data?: { data?: ContractErrorVo } } };
  return candidate?.responseData ?? candidate?.response?.data?.data;
}
