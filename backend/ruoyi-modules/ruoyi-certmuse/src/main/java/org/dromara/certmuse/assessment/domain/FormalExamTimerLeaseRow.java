package org.dromara.certmuse.assessment.domain;

import java.time.OffsetDateTime;
import lombok.Data;

/** Active browser/device timing lease for a formal examination. */
@Data
public class FormalExamTimerLeaseRow {
    private Long sessionId;
    private Integer questionOrder;
    private Long attemptId;
    private String leaseId;
    private OffsetDateTime lastHeartbeatAt;
}
