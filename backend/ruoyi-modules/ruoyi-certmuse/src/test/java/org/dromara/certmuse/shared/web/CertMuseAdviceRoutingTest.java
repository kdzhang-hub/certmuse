package org.dromara.certmuse.shared.web;

import java.util.List;
import org.dromara.certmuse.learning.support.OnboardingException;
import org.dromara.certmuse.question.controller.QuestionController;
import org.dromara.certmuse.question.controller.QuestionExceptionHandler;
import org.dromara.certmuse.question.service.QuestionService;
import org.dromara.certmuse.question.support.QuestionException;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class CertMuseAdviceRoutingTest {

    @Test
    void routesDomainFailuresToDomainAdviceAndUnexpectedFailuresToModuleAdvice() throws Exception {
        QuestionService service = mock(QuestionService.class);
        when(service.detail("domain", null))
            .thenThrow(new QuestionException(409, "QUESTION_STATE_CONFLICT", "状态已变化"));
        when(service.detail("illegal-argument", null))
            .thenThrow(new IllegalArgumentException("internal detail"));
        when(service.detail("shared", null))
            .thenThrow(new CertMuseApiException(
                409, "SHARED_STATE_CONFLICT", "共享状态已变化", false, null,
                List.of(), null, null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new QuestionController(service))
            .setControllerAdvice(
                new QuestionExceptionHandler(),
                new CertMuseExceptionHandler(),
                new GlobalExceptionHandler())
            .build();

        mvc.perform(get("/api/admin/questions/domain"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.data.errorCode").value("QUESTION_STATE_CONFLICT"))
            .andExpect(jsonPath("$.data.traceId").doesNotExist());

        mvc.perform(get("/api/admin/questions/shared"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.data.errorCode").value("SHARED_STATE_CONFLICT"))
            .andExpect(jsonPath("$.data.retryable").value(false));

        mvc.perform(get("/api/admin/questions/illegal-argument"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.data.errorCode").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.data.traceId").isNotEmpty())
            .andExpect(jsonPath("$.msg").value("系统暂时无法处理请求"));
    }

    @Test
    void mapsOnboardingStateFailuresThroughTheSharedBoundary() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OnboardingFailureController())
            .setControllerAdvice(new CertMuseExceptionHandler())
            .build();

        mvc.perform(get("/test/onboarding/failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("学习状态异常，请联系管理员"))
            .andExpect(jsonPath("$.data.errorCode").value("ONBOARDING_STATE_INVALID"))
            .andExpect(jsonPath("$.data.retryable").value(false))
            .andExpect(jsonPath("$.data.traceId").isNotEmpty());
    }

    @RestController
    static class OnboardingFailureController {

        @GetMapping("/test/onboarding/failure")
        void failure() {
            throw new OnboardingException("学习状态异常，请联系管理员");
        }
    }

}
