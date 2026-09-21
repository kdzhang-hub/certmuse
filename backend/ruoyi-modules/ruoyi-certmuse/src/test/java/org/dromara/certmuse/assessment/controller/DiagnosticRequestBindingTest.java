package org.dromara.certmuse.assessment.controller;

import org.dromara.certmuse.assessment.service.DiagnosticService;
import org.dromara.certmuse.shared.web.CertMuseExceptionHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class DiagnosticRequestBindingTest {

    @Test
    void mapsRequiredTimerFieldsToTheFrozenDiagnosticError() throws Exception {
        mvc().perform(post("/api/assessment/diagnostics/1/timer-events")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data.errorCode").value("DIAGNOSTIC_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.retryable").value(false))
            .andExpect(jsonPath("$.data.traceId").doesNotExist())
            .andExpect(jsonPath("$.data.nextAction").doesNotExist())
            .andExpect(jsonPath("$.data.fieldErrors[?(@.field == 'eventType')].code")
                .value("REQUIRED"))
            .andExpect(jsonPath("$.data.fieldErrors[?(@.field == 'questionOrder')].code")
                .value("REQUIRED"));
    }

    @Test
    void mapsNonPositiveTimerQuestionOrdersToOutOfRange() throws Exception {
        mvc().perform(post("/api/assessment/diagnostics/1/timer-events")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("""
                    {"eventType":"ENTER","questionOrder":0,"leaseId":null}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.errorCode").value("DIAGNOSTIC_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("questionOrder"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("OUT_OF_RANGE"));
    }

    @Test
    void keepsTheHeaderNameForAMissingRequestId() throws Exception {
        mvc().perform(post("/api/assessment/diagnostics/1/timer-events")
                .contentType("application/json")
                .content("""
                    {"eventType":"ENTER","questionOrder":1,"leaseId":null}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.errorCode").value("DIAGNOSTIC_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("X-Request-Id"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("REQUIRED"));
    }

    @Test
    void rejectsMalformedJsonWithoutLeakingParserDetails() throws Exception {
        mvc().perform(post("/api/assessment/diagnostics/1/timer-events")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.msg").value("请求参数格式不正确"))
            .andExpect(jsonPath("$.data.errorCode").value("DIAGNOSTIC_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors").isEmpty());
    }

    @Test
    void mapsPathTypeMismatchToTheFrozenDiagnosticError() throws Exception {
        mvc().perform(get("/api/assessment/diagnostics/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data.errorCode").value("DIAGNOSTIC_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("sessionId"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("INVALID_FORMAT"));
    }

    private static MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                new DiagnosticController(mock(DiagnosticService.class)))
            .setControllerAdvice(
                new DiagnosticExceptionHandler(),
                new CertMuseExceptionHandler())
            .build();
    }
}
