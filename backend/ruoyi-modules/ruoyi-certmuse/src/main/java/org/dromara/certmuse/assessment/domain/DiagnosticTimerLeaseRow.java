package org.dromara.certmuse.assessment.domain;

import lombok.Data;

import java.time.OffsetDateTime;

/** Active diagnostic timer lease projection. */
@Data
public class DiagnosticTimerLeaseRow {
    private Long sessionId; private Integer questionOrder; private Long attemptId;
    private String leaseId; private OffsetDateTime lastHeartbeatAt;
}
