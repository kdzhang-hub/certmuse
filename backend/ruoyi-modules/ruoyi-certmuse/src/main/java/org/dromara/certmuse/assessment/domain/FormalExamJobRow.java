package org.dromara.certmuse.assessment.domain;

import lombok.Data;

/** Claimed formal-exam result job. */
@Data
public class FormalExamJobRow {
    private Long id;
    private String businessKey;
    private String payload;
}
