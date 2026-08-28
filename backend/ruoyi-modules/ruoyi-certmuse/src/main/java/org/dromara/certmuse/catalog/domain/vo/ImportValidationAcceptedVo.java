package org.dromara.certmuse.catalog.domain.vo;

/**
 * Import validation scheduling response.
 */
public record ImportValidationAcceptedVo(
    String id,
    String status,
    String currentStage,
    boolean accepted
) {
}
