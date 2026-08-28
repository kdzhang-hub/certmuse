import type { StartKnowledgePracticeRequest } from '@/api/certmuse/assessment/knowledge-practices';
import type { RequestIdentity } from '@/utils/learning-goal';

export function practicePayloadFingerprint(payload: StartKnowledgePracticeRequest): string {
  return JSON.stringify({ knowledgePointId: payload.knowledgePointId, expectedGoalVersion: payload.expectedGoalVersion });
}

export function practiceRequestIdentityFor(
  payload: StartKnowledgePracticeRequest,
  current?: RequestIdentity,
  uuid: () => string = () => crypto.randomUUID()
): RequestIdentity {
  const fingerprint = practicePayloadFingerprint(payload);
  return current?.fingerprint === fingerprint ? current : { fingerprint, id: uuid().toLowerCase() };
}
