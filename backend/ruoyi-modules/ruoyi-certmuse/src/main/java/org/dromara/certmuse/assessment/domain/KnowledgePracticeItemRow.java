package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Frozen U09 item plus its optional submitted answer. */
@Data
public class KnowledgePracticeItemRow {
    private Long sessionQuestionId;
    private Long sessionId;
    private Long userId;
    private Long goalId;
    private Long ruleVersionId;
    private Long examSubjectId;
    private String evidenceGroupKey;
    private String difficultySnapshot;
    private Integer questionOrder;
    private Integer totalCount;
    private String sessionStatus;
    private String presentationSnapshot;
    private String gradingSnapshot;
    private String knowledgeSnapshot;
    private Long attemptId;
    private String answerData;
    private Boolean correct;
}
