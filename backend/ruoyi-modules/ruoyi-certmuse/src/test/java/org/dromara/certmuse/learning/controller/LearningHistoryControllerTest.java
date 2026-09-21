package org.dromara.certmuse.learning.controller;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

@Tag("dev")
class LearningHistoryControllerTest {
    @Test
    void declaresFrozenRouteAndAndPermissions() {
        RequestMapping route = LearningHistoryController.class.getAnnotation(RequestMapping.class);
        SaCheckPermission permission = LearningHistoryController.class.getAnnotation(SaCheckPermission.class);

        assertThat(route.value()).containsExactly("/api/learning/history");
        assertThat(permission.value()).containsExactly("certmuse:student", "certmuse:learning:history:query");
        assertThat(permission.mode()).isEqualTo(SaMode.AND);
    }

    @Test
    void exposesExamRoutesAndRemovesSimulationRoutes() {
        assertThat(LearningHistoryController.class.getDeclaredMethods())
            .filteredOn(method -> method.isAnnotationPresent(GetMapping.class))
            .extracting(method -> method.getAnnotation(GetMapping.class).value()[0])
            .contains("/exams", "/exams/{sessionId}", "/practices", "/practices/{sessionId}");
    }
}
