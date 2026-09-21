package org.dromara.certmuse.learning.controller;

import org.dromara.certmuse.learning.domain.bo.CreateLearningGoalBo;
import org.dromara.certmuse.learning.service.LearningGoalService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class LearningGoalRequestBindingTest {

    @Test
    void bindsAValidRequestToTheCommandModel() throws Exception {
        CreateLearningGoalBo command = JsonMapper.builder().build().readValue("""
            {"certificationId":"9","targetExamYear":2026,"targetExamMonth":11,"dailyMinutes":30}
            """, CreateLearningGoalBo.class);

        assertEquals("9", command.getCertificationId());
        assertEquals(2026, command.getTargetExamYear());
        assertEquals(11, command.getTargetExamMonth());
        assertEquals(30, command.getDailyMinutes());
    }

    @Test
    void rejectsMissingRequiredField() throws Exception {
        mvc().perform(post("/api/learning/goals")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("""
                    {"targetExamYear":2026,"targetExamMonth":11,"dailyMinutes":30}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data.errorCode").value("GOAL_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("certificationId"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("REQUIRED"));
    }

    @Test
    void classifiesDailyMinutesRangeFailures() throws Exception {
        mvc().perform(post("/api/learning/goals")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("""
                    {"certificationId":"9","targetExamYear":2026,"targetExamMonth":11,"dailyMinutes":0}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("dailyMinutes"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("OUT_OF_RANGE"));

        mvc().perform(post("/api/learning/goals")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("""
                    {"certificationId":"9","targetExamYear":2026,"targetExamMonth":11,"dailyMinutes":10001}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("dailyMinutes"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("OUT_OF_RANGE"));
    }

    @Test
    void classifiesCertificationIdFormatFailures() throws Exception {
        mvc().perform(post("/api/learning/goals")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("""
                    {"certificationId":"invalid","targetExamYear":2026,"targetExamMonth":11,"dailyMinutes":30}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("certificationId"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void rejectsInvalidJsonValueTypeWithoutLeakingParserDetails() throws Exception {
        mvc().perform(post("/api/learning/goals")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("""
                    {"certificationId":"9","targetExamYear":"bad","targetExamMonth":11,"dailyMinutes":30}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value("请求参数格式不正确"))
            .andExpect(jsonPath("$.data.errorCode").value("GOAL_REQUEST_INVALID"));
    }

    @Test
    void rejectsUnknownRequestProperty() throws Exception {
        mvc().perform(post("/api/learning/goals")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("""
                    {"certificationId":"9","targetExamYear":2026,"targetExamMonth":11,"dailyMinutes":30,"requestId":"forbidden"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value("请求参数格式不正确"))
            .andExpect(jsonPath("$.data.errorCode").value("GOAL_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors").isEmpty());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc().perform(post("/api/learning/goals")
                .header("X-Request-Id", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd")
                .contentType("application/json")
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value("请求参数格式不正确"))
            .andExpect(jsonPath("$.data.errorCode").value("GOAL_REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors").isEmpty());
    }

    private static MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new LearningGoalController(mock(LearningGoalService.class)))
            .setControllerAdvice(new LearningGoalExceptionHandler())
            .build();
    }
}
