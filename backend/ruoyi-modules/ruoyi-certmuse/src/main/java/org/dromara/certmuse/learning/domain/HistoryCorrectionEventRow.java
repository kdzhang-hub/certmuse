package org.dromara.certmuse.learning.domain;

import java.time.OffsetDateTime;
import lombok.Data;

/** Persistence projection used to derive correction history events. */
@Data
public class HistoryCorrectionEventRow {
    private long factId;
    private long attemptId;
    private String sessionType;
    private OffsetDateTime occurredAt;
    private boolean skipped;
    private boolean correction;
}
