package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

public record DiagnosticPreflightVo(GoalVo goal, String diagnosticStatus, String nextAction,
                                    RevisionVo diagnosticRevision, ExistingSessionVo existingSession,
                                    List<BlockerVo> blockers) {
    public record GoalVo(String id, String certificationId, String certificationName, String syllabusVersionId,
                         String syllabusVersionName, Long version) { }
    public record RevisionVo(String id, int questionCount, Integer estimatedMinutes, List<SubjectVo> subjectBreakdown) { }
    public record SubjectVo(String examSubjectId, String subjectName, int questionCount) { }
    public record ExistingSessionVo(String id, int answeredCount, int totalCount, int lastQuestionOrder, long sessionVersion) { }
    public record BlockerVo(String code, String message) { }
}
