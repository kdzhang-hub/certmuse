package org.dromara.certmuse.question.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.certmuse.question.domain.bo.CollectionQueryBo;
import org.dromara.certmuse.question.domain.bo.CollectionRejectBo;
import org.dromara.certmuse.question.domain.bo.CollectionRenameBo;
import org.dromara.certmuse.question.domain.bo.CollectionRevisionCreateBo;
import org.dromara.certmuse.question.domain.bo.CollectionSaveBo;
import org.dromara.certmuse.question.service.CollectionService;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("dev")
class CollectionControllerTest {
    @Test
    void usesOneControllerForCollectionAndRevisionRoutes() {
        new CollectionController(mock(CollectionService.class));
        assertThat(CollectionController.class.getAnnotation(RequestMapping.class).value())
            .containsExactly("/api/admin");
    }

    @Test
    void exposesCollectionPermissions() throws Exception {
        assertPermission("list", "certmuse:question:collection:list", CollectionQueryBo.class);
        assertPermission("manageList", "certmuse:question:collection:list", CollectionQueryBo.class);
        assertPermission("detail", "certmuse:question:collection:list", String.class);
        assertPermission("revisionDetail", "certmuse:question:collection:list", String.class);
        assertPermission("create", "certmuse:question:collection:add", String.class, CollectionSaveBo.class);
        assertPermission("save", "certmuse:question:collection:edit", String.class, String.class, CollectionSaveBo.class);
        assertPermission("rename", "certmuse:question:collection:edit", String.class, String.class, CollectionRenameBo.class);
        assertPermission("deleteDraft", "certmuse:question:collection:remove", String.class, String.class);
        assertPermission("createRevision", "certmuse:question:collection:add", String.class, String.class,
            CollectionRevisionCreateBo.class);
        assertPermissions("submitReview", new String[]{"certmuse:question:collection:submit-review",
            "certmuse:question:submit-review"}, String.class, String.class);
        assertPermission("approve", "certmuse:question:collection:review", String.class, String.class);
        assertPermission("reject", "certmuse:question:collection:review", String.class, String.class, CollectionRejectBo.class);
        assertPermission("offline", "certmuse:question:collection:offline", String.class, String.class);
    }

    @Test
    void excludesCollectionContentFromOperationLogs() throws Exception {
        assertContentLogDisabled("create", String.class, CollectionSaveBo.class);
        assertContentLogDisabled("save", String.class, String.class, CollectionSaveBo.class);
        assertContentLogDisabled("rename", String.class, String.class, CollectionRenameBo.class);
        assertContentLogDisabled("deleteDraft", String.class, String.class);
        assertContentLogDisabled("createRevision", String.class, String.class, CollectionRevisionCreateBo.class);
        assertContentLogDisabled("submitReview", String.class, String.class);
        assertContentLogDisabled("approve", String.class, String.class);
        assertContentLogDisabled("reject", String.class, String.class, CollectionRejectBo.class);
        assertContentLogDisabled("offline", String.class, String.class);
    }

    @Test
    void doesNotExposeStandalonePublishCheck() {
        assertThat(java.util.Arrays.stream(CollectionController.class.getMethods())
            .noneMatch(method -> method.getName().equals("publishCheck"))).isTrue();
    }

    @Test
    void delegatesCollectionQueriesAndLifecycleCommandsToTheService() {
        CollectionService service = mock(CollectionService.class);
        CollectionController controller = new CollectionController(service);
        CollectionQueryBo query = mock(CollectionQueryBo.class);
        CollectionSaveBo save = mock(CollectionSaveBo.class);
        CollectionRevisionCreateBo createRevision = mock(CollectionRevisionCreateBo.class);
        CollectionRejectBo reject = mock(CollectionRejectBo.class);
        CollectionRenameBo rename = mock(CollectionRenameBo.class);

        controller.list(query);
        controller.manageList(query);
        controller.detail("collection-1");
        controller.revisionDetail("revision-1");
        controller.create("request-1", save);
        controller.save("revision-1", "request-2", save);
        controller.rename("collection-1", "request-rename", rename);
        controller.deleteDraft("revision-1", "request-3");
        controller.createRevision("collection-1", "request-4", null);
        controller.createRevision("collection-1", "request-5", createRevision);
        controller.submitReview("revision-1", "request-6");
        controller.approve("revision-1", "request-7");
        controller.reject("revision-1", "request-8", reject);
        controller.offline("revision-1", "request-9");

        org.mockito.Mockito.verify(service).list(query);
        org.mockito.Mockito.verify(service).manageList(query);
        org.mockito.Mockito.verify(service).detail("collection-1");
        org.mockito.Mockito.verify(service).revisionDetail("revision-1");
        org.mockito.Mockito.verify(service).create("request-1", save);
        org.mockito.Mockito.verify(service).save("revision-1", "request-2", save);
        org.mockito.Mockito.verify(service).rename("collection-1", "request-rename", rename);
        org.mockito.Mockito.verify(service).deleteDraft("revision-1", "request-3");
        org.mockito.Mockito.verify(service).createRevision(org.mockito.ArgumentMatchers.eq("collection-1"),
            org.mockito.ArgumentMatchers.eq("request-4"), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(service).createRevision("collection-1", "request-5", createRevision);
        org.mockito.Mockito.verify(service).submitReview("revision-1", "request-6");
        org.mockito.Mockito.verify(service).approve("revision-1", "request-7");
        org.mockito.Mockito.verify(service).reject("revision-1", "request-8", null);
        org.mockito.Mockito.verify(service).offline("revision-1", "request-9");
    }

    private static void assertPermission(String methodName, String permission, Class<?>... parameters) throws Exception {
        Method method = CollectionController.class.getMethod(methodName, parameters);
        assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly(permission);
    }

    private static void assertPermissions(String methodName, String[] permissions, Class<?>... parameters) throws Exception {
        Method method = CollectionController.class.getMethod(methodName, parameters);
        assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly(permissions);
        assertThat(method.getAnnotation(SaCheckPermission.class).mode()).isEqualTo(cn.dev33.satoken.annotation.SaMode.AND);
    }

    private static void assertContentLogDisabled(String methodName, Class<?>... parameters) throws Exception {
        Method method = CollectionController.class.getMethod(methodName, parameters);
        Log log = method.getAnnotation(Log.class);
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }
}
