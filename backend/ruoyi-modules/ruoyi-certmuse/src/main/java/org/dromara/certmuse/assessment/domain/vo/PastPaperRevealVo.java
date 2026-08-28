package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

/** Login-only answer and analysis disclosed for one displayed question. */
public record PastPaperRevealVo(List<String> correctOptionLabels, String analysis, OffsetDateTime disclosedAt,
                                OffsetDateTime profileEvidenceBlockedUntil) {}
