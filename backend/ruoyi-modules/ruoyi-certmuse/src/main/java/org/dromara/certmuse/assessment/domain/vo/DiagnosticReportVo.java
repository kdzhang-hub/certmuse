package org.dromara.certmuse.assessment.domain.vo;

import tools.jackson.databind.JsonNode;

/** The report body is persisted real output, never a computed presentation fallback. */
public record DiagnosticReportVo(String diagnosticStatus, String profileStatus, String nextAction,
                                 JsonNode report) { }
