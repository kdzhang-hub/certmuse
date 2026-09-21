package org.dromara.certmuse.assessment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dromara.certmuse.assessment.service.SimulationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

@Tag("dev")
class SimulationRequestBindingTest {
    @Test
    void rejectsOutOfRangePaginationWithTheFrozenFieldError() throws Exception {
        mvc().perform(get("/api/assessment/simulations?pageSize=51"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data.errorCode").value("SIMULATION_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.retryable").value(false))
            .andExpect(jsonPath("$.data.traceId").doesNotExist())
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("pageSize"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("OUT_OF_RANGE"));
    }

    @Test
    void keepsTheHeaderNameForAMissingStartRequestId() throws Exception {
        mvc().perform(post("/api/assessment/simulations/11/sessions")
                .contentType("application/json")
                .content("""
                    {"expectedRevisionId":"12","expectedGoalVersion":1}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.errorCode").value("SIMULATION_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("X-Request-Id"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("REQUIRED"));
    }

    @Test
    void rejectsMalformedJsonWithoutParserDetails() throws Exception {
        mvc().perform(post("/api/assessment/simulations/11/sessions")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.msg").value("请求参数格式不正确"))
            .andExpect(jsonPath("$.data.errorCode").value("SIMULATION_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("body"))
            .andExpect(jsonPath("$.data.fieldErrors[0].message").value("请求体不是有效JSON"));
    }

    @Test
    void mapsInvalidBodyFieldsToTheFrozenError() throws Exception {
        mvc().perform(post("/api/assessment/simulations/11/sessions")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.errorCode").value("SIMULATION_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[?(@.field == 'expectedRevisionId')].code").value("REQUIRED"))
            .andExpect(jsonPath("$.data.fieldErrors[?(@.field == 'expectedGoalVersion')].code").value("REQUIRED"));
    }

    private static MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new SimulationController(mock(SimulationService.class)))
            .setControllerAdvice(new SimulationExceptionHandler())
            .build();
    }
}
