package org.dromara.certmuse.learning.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import org.dromara.certmuse.learning.support.KnowledgePointDirectoryException;
import org.dromara.certmuse.learning.service.KnowledgePointDirectoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;

@Tag("dev")
class KnowledgePointDirectoryControllerTest {

    @Test
    void declaresFrozenRouteAndBothRequiredPermissions() throws Exception {
        Method method = KnowledgePointDirectoryController.class.getMethod("lookup", jakarta.servlet.http.HttpServletRequest.class);
        assertThat(KnowledgePointDirectoryController.class.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class).value())
            .containsExactly("/api/learning/knowledge-points");
        assertThat(method.getAnnotation(GetMapping.class)).isNotNull();
        assertThat(method.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("certmuse:student", "certmuse:learning:knowledge-point:query");
        assertThat(method.getAnnotation(SaCheckPermission.class).mode()).isEqualTo(SaMode.AND);
    }

    @Test
    void rejectsExtraOrRepeatedQueryParametersBeforeCallingService() {
        KnowledgePointDirectoryService service = Mockito.mock(KnowledgePointDirectoryService.class);
        KnowledgePointDirectoryController controller = new KnowledgePointDirectoryController(service);
        MockHttpServletRequest extra = new MockHttpServletRequest("GET", "/api/learning/knowledge-points");
        extra.addParameter("ids", "1");
        extra.addParameter("userId", "2");
        MockHttpServletRequest repeated = new MockHttpServletRequest("GET", "/api/learning/knowledge-points");
        repeated.addParameter("ids", "1", "2");

        KnowledgePointDirectoryException extraException = org.junit.jupiter.api.Assertions.assertThrows(
            KnowledgePointDirectoryException.class, () -> controller.lookup(extra));
        KnowledgePointDirectoryException repeatedException = org.junit.jupiter.api.Assertions.assertThrows(
            KnowledgePointDirectoryException.class, () -> controller.lookup(repeated));

        assertThat(extraException.data().fieldErrors()).isEmpty();
        assertThat(repeatedException.data().fieldErrors()).singleElement()
            .satisfies(error -> assertThat(error.field()).isEqualTo("ids"));
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void marksMissingIdsAsRequiredBeforeCallingService() {
        KnowledgePointDirectoryService service = Mockito.mock(KnowledgePointDirectoryService.class);
        KnowledgePointDirectoryController controller = new KnowledgePointDirectoryController(service);

        KnowledgePointDirectoryException exception = org.junit.jupiter.api.Assertions.assertThrows(
            KnowledgePointDirectoryException.class,
            () -> controller.lookup(new MockHttpServletRequest("GET", "/api/learning/knowledge-points")));

        assertThat(exception.data().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("ids");
            assertThat(error.code()).isEqualTo("REQUIRED");
        });
        Mockito.verifyNoInteractions(service);
    }
}
