package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Read projection for one current published past-paper revision. */
@Data
public class PastPaperPaperRow {
    private Long collectionId;
    private Long revisionId;
    private Long certificationId;
    private String collectionName;
    private String certificationName;
    private Integer questionCount;
    private String totalReportScore;
    private Integer durationMinutes;
    private String questionTypesJson;
    private String subjectsJson;
    private Integer examYear;
    private Integer examMonth;
    private String paperTypeCode;
    private String paperTypeName;
    private String publishedTime;
    private Long activeExamSessionId;
    private String activeExamSessionStatus;
}
