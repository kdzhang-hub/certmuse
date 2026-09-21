package org.dromara.certmuse.question.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.certmuse.question.domain.bo.QuestionReviewRejectBo;
import org.dromara.certmuse.question.domain.vo.QuestionReviewMutationVo;
import org.dromara.certmuse.question.domain.vo.QuestionSubmitReviewResultVo;
import org.dromara.certmuse.question.service.QuestionService;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("dev")
class QuestionControllerTest {
    @Test
    void exposesTheFrozenPermissions() throws Exception {
        new QuestionController(mock(QuestionService.class));
        assertPermission("list", "certmuse:question:list", org.dromara.certmuse.question.domain.bo.QuestionQueryBo.class);
        assertPermission("detail", "certmuse:question:list", String.class, String.class);
        assertPermission("save", "certmuse:question:edit", String.class, String.class,
            org.dromara.certmuse.question.domain.bo.QuestionSaveBo.class);
        assertPermission("preview", "certmuse:question:preview", String.class, String.class);
        assertPermission("delete", "certmuse:question:remove", String.class, String.class);
    }

    @Test
    void doesNotAuditQuestionContent() throws Exception {
        Method method = QuestionController.class.getMethod("save", String.class, String.class,
            org.dromara.certmuse.question.domain.bo.QuestionSaveBo.class);

        Log log = method.getAnnotation(Log.class);

        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    @Test
    void delegatesQuestionQueriesAndWritesToTheService() {
        QuestionService service = mock(QuestionService.class);
        QuestionController controller = new QuestionController(service);
        org.dromara.certmuse.question.domain.bo.QuestionQueryBo query = mock(
            org.dromara.certmuse.question.domain.bo.QuestionQueryBo.class);
        org.dromara.certmuse.question.domain.bo.QuestionSaveBo command = mock(
            org.dromara.certmuse.question.domain.bo.QuestionSaveBo.class);

        controller.list(query);
        controller.detail("question-1", null);
        controller.detail("question-1", "revision-1");
        controller.save("question-1", "request-1", command);
        controller.preview("question-1", "revision-1");
        controller.delete("question-1", "request-2");

        org.mockito.Mockito.verify(service).list(query);
        org.mockito.Mockito.verify(service).detail("question-1", null);
        org.mockito.Mockito.verify(service).detail("question-1", "revision-1");
        org.mockito.Mockito.verify(service).save("question-1", "request-1", command);
        org.mockito.Mockito.verify(service).preview("question-1", "revision-1");
        org.mockito.Mockito.verify(service).delete("question-1", "request-2");
    }

    @Test
    void exposesReviewRoutesWithReviewPermissionAndRedactedLogs() throws Exception {
        new QuestionReviewController(mock(QuestionService.class));
        assertPermission(QuestionReviewController.class, "submitReview", "certmuse:question:submit-review", String.class, String.class);
        assertPermission(QuestionReviewController.class, "approve", "certmuse:question:review", String.class, String.class);
        assertPermission(QuestionReviewController.class, "reject", "certmuse:question:review", String.class, String.class,
            QuestionReviewRejectBo.class);
        assertPermission(QuestionReviewController.class, "takeOffline", "certmuse:question:offline", String.class, String.class);

        Method submit = QuestionReviewController.class.getMethod("submitReview", String.class, String.class);
        Method approve = QuestionReviewController.class.getMethod("approve", String.class, String.class);
        Method reject = QuestionReviewController.class.getMethod("reject", String.class, String.class, QuestionReviewRejectBo.class);
        assertThat(submit.getAnnotation(Log.class).isSaveRequestData()).isFalse();
        assertThat(submit.getAnnotation(Log.class).isSaveResponseData()).isFalse();
        assertThat(approve.getAnnotation(Log.class).isSaveRequestData()).isFalse();
        assertThat(approve.getAnnotation(Log.class).isSaveResponseData()).isFalse();
        assertThat(reject.getAnnotation(Log.class).isSaveRequestData()).isFalse();
        assertThat(reject.getAnnotation(Log.class).isSaveResponseData()).isFalse();
        assertThat(QuestionReviewMutationVo.class.getRecordComponents()).hasSize(4);
        assertThat(QuestionSubmitReviewResultVo.class.getRecordComponents()).hasSize(4);
    }

    @Test
    void delegatesMissingRejectBodyAsNullForServiceValidation() {
        QuestionService service = mock(QuestionService.class);
        QuestionReviewController controller = new QuestionReviewController(service);

        controller.reject("200", "00000000-0000-0000-0000-000000000001", null);

        verify(service).reject("200", "00000000-0000-0000-0000-000000000001", null);
    }

    @Test
    void delegatesAllQuestionReviewCommandsToTheService() {
        QuestionService service = mock(QuestionService.class);
        QuestionReviewController controller = new QuestionReviewController(service);
        QuestionReviewRejectBo reject = mock(QuestionReviewRejectBo.class);

        controller.submitReview("200", "request-1");
        controller.approve("200", "request-2");
        controller.reject("200", "request-3", reject);
        controller.takeOffline("200", "request-4");

        verify(service).submitReview("200", "request-1");
        verify(service).approve("200", "request-2");
        verify(service).reject("200", "request-3", null);
        verify(service).takeOffline("200", "request-4");
    }

    private static void assertPermission(String methodName, String permission, Class<?>... parameters) throws Exception {
        assertPermission(QuestionController.class, methodName, permission, parameters);
    }

    private static void assertPermission(Class<?> controllerType, String methodName, String permission,
                                         Class<?>... parameters) throws Exception {
        Method method = controllerType.getMethod(methodName, parameters);
        assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly(permission);
    }
}
