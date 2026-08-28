package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Read-only published simulation question-stem preview enabled by U13 V1.2. */
public record SimulationPreviewVo(String collectionId, String revisionId, String collectionName,
                                  List<SimulationPreviewQuestionVo> questions) {
}
