package org.dromara.certmuse.learning.controller;

import org.dromara.certmuse.learning.domain.bo.CreateLearningGoalBo;
import org.dromara.certmuse.learning.domain.vo.CreateLearningGoalResultVo;
import org.dromara.certmuse.learning.domain.vo.LearningGoalOptionsVo;
import org.dromara.certmuse.learning.domain.vo.OnboardingStatusVo;
import org.dromara.certmuse.learning.service.LearningGoalService;
import org.dromara.certmuse.learning.service.OnboardingService;
import org.dromara.certmuse.learning.support.LearningGoalException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BindException;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class LearningControllersTest {
    @Test
    void learningGoalEndpointsDelegateWithTheAuthenticatedLearner() {
        LearningGoalService service = mock(LearningGoalService.class);
        LearningGoalController controller = new LearningGoalController(service);
        LearningGoalOptionsVo options = new LearningGoalOptionsVo("now", "Asia/Shanghai", List.of(), List.of(), null, null);
        CreateLearningGoalResultVo result = new CreateLearningGoalResultVo(null, "START_DIAGNOSTIC");
        CreateLearningGoalBo command = validCommand();
        when(service.options(42L)).thenReturn(options);
        when(service.create(any(Long.class), any(String.class), any(CreateLearningGoalBo.class))).thenReturn(result);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(42L);
            assertThat(controller.options().getData()).isSameAs(options);
            assertThat(controller.create("request", command).getData()).isSameAs(result);
        }

        verify(service).options(42L);
        verify(service).create(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.eq("request"),
            org.mockito.ArgumentMatchers.argThat(actualCommand -> actualCommand.getCertificationId().equals("9")
                && actualCommand.getTargetExamYear() == 2026 && actualCommand.getTargetExamMonth() == 11
                && actualCommand.getDailyMinutes() == 30));
    }

    @Test
    void onboardingEndpointUsesTheAuthenticatedLearner() {
        OnboardingService service = mock(OnboardingService.class);
        OnboardingController controller = new OnboardingController(service);
        OnboardingStatusVo status = new OnboardingStatusVo();
        status.setNextAction("SET_GOAL");
        when(service.status(42L)).thenReturn(status);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(42L);
            assertThat(controller.status().getData()).isSameAs(status);
        }
        verify(service).status(42L);
    }

    @Test
    void exceptionHandlerPreservesDomainErrorsAndMapsCurrentRequestBindingFailures() {
        LearningGoalExceptionHandler handler = new LearningGoalExceptionHandler();
        LearningGoalException domain = new LearningGoalException(409, "GOAL_ALREADY_EXISTS", "已有目标");

        var expected = handler.handle(domain);

        assertThat(expected.getStatusCode().value()).isEqualTo(409);
        assertThat(expected.getBody().getMsg()).isEqualTo("已有目标");
        assertThat(expected.getBody().getData().errorCode()).isEqualTo("GOAL_ALREADY_EXISTS");
        assertThat(expected.getBody().getData().traceId()).isNull();

        CreateLearningGoalBo target = new CreateLearningGoalBo();
        DataBinder methodBinder = binder(target);
        methodBinder.getBindingResult().rejectValue("certificationId", "invalid", "资格错误");
        MethodParameter parameter = mock(MethodParameter.class);
        MethodArgumentNotValidException method = new MethodArgumentNotValidException(parameter, methodBinder.getBindingResult());

        DataBinder bindBinder = binder(target);
        bindBinder.getBindingResult().rejectValue("dailyMinutes", "invalid", "时长错误");
        BindException bind = new BindException(bindBinder.getBindingResult());
        HttpMessageNotReadableException unreadable = new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0]));

        assertThat(handler.handleInvalidRequest(method).getBody().getData().fieldErrors()).extracting("field", "code")
            .containsExactly(org.assertj.core.groups.Tuple.tuple("certificationId", "INVALID_FORMAT"));
        assertThat(handler.handleInvalidRequest(bind).getBody().getData().fieldErrors()).extracting("field", "code")
            .containsExactly(org.assertj.core.groups.Tuple.tuple("dailyMinutes", "INVALID_FORMAT"));
        assertThat(handler.handleInvalidRequest(unreadable).getBody().getData().fieldErrors()).isEmpty();
    }

    private static CreateLearningGoalBo validCommand() {
        CreateLearningGoalBo command = new CreateLearningGoalBo();
        command.setCertificationId("9");
        command.setTargetExamYear(2026);
        command.setTargetExamMonth(11);
        command.setDailyMinutes(30);
        return command;
    }

    private static DataBinder binder(Object target) {
        return new DataBinder(target, "command");
    }
}
