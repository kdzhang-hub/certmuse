package org.dromara.certmuse.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.dromara.certmuse.profile.domain.vo.OverallScoreVo;
import org.dromara.certmuse.profile.service.LearningProfileQueryService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag("dev")
class LearningProfileControllerTest {

    @Test
    void exposesParameterFreeGetWithAndPermissions() throws Exception {
        RequestMapping base = LearningProfileController.class.getAnnotation(RequestMapping.class);
        var method = LearningProfileController.class.getMethod("overallScore");
        GetMapping get = method.getAnnotation(GetMapping.class);
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);

        assertThat(base.value()).containsExactly("/api/learning/profile");
        assertThat(get.value()).containsExactly("/overall-score");
        assertThat(method.getParameterCount()).isZero();
        assertThat(permission.mode()).isEqualTo(SaMode.AND);
        assertThat(permission.value()).containsExactly(
            "certmuse:student", "certmuse:profile:overall-score:query");
    }

    @Test
    void usesAuthenticatedUserAndDisablesHttpCaching() {
        LearningProfileQueryService service = mock(LearningProfileQueryService.class);
        OffsetDateTime calculatedTime = OffsetDateTime.parse("2026-08-17T10:20:00+08:00");
        OverallScoreVo score = new OverallScoreVo(
            "系统架构设计师", new BigDecimal("72.4375"), calculatedTime);
        when(service.overallScore(7L)).thenReturn(score);

        try (MockedStatic<LoginHelper> login = Mockito.mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var response = new LearningProfileController(service).overallScore();

            assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(response.getBody().getCode()).isEqualTo(200);
            assertThat(response.getBody().getData()).isSameAs(score);
        }
    }
}
