package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** One page item of a publicly browsable past paper. */
public record PastPaperListVo(String collectionId, String revisionId, String collectionName, String certificationName,
                              List<SubjectVo> subjects, int questionCount, String totalReportScore,
                              int durationMinutes, List<String> questionTypes, Integer examYear, Integer examMonth,
                              String paperTypeCode, String paperTypeName, AccessVo practiceAccess, AccessVo examAccess) {
    public record SubjectVo(String id, String name) {}
    public record AccessVo(String action, boolean canStart, String blockCode) {}
}
