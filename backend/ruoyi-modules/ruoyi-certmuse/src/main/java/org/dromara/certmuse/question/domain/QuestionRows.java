package org.dromara.certmuse.question.domain;

import lombok.Data;

import java.time.OffsetDateTime;

public final class QuestionRows {
    private QuestionRows() { }

    @Data public static class Revision {
        private Long questionId; private Long revisionId; private Integer revisionNo; private Long rowVersion;
        private String questionCode; private Long examSubjectId; private String examSubjectName;
        private Long certificationId; private String certificationName; private String questionType;
        private String difficulty; private Integer estimatedSeconds; private String stem; private String answer;
        private String analysis; private String commonMistakes; private String status; private String reviewOpinion;
        private OffsetDateTime updatedTime;
        private Long createBy; private Long createDept; private String contentHash;
        private String sourceType; private String sourceName; private String sourceExternalId; private String sourceLocator;
        private String authorizationStatus; private String authorizationNote; private String knowledgeMappingStatus;
    }
    @Data public static class ListItem {
        private String questionId; private String revisionId; private Integer revisionNo; private String questionCode;
        private String syllabusVersionId; private String syllabusVersionName; private String stemSummary;
        private String examSubjectId; private String examSubjectName; private String questionType;
        private String difficulty; private String status; private Boolean hasReviewOpinion; private OffsetDateTime updatedTime;
    }
    @Data public static class Option { private Long id; private String label; private String content; private Integer sortOrder; }
    @Data public static class Image { private Long id; private Integer sortOrder; private String sourceUrl; private String alt; private String storagePath; }
    @Data public static class Knowledge { private Long knowledgePointId; private String knowledgePointLabel; private String relationRole; private Integer sortOrder; private Long syllabusVersionId; private Long examSubjectId; private Boolean leaf; }
    @Data public static class IdLabel { private String id; private String label; }
    @Data public static class Idempotency { private Long id; private String payloadHash; private String status; private Long resourceId; private Integer responseStatus; private String responseBody; }
}
