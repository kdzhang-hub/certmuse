package org.dromara.certmuse.assessment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.annotation.SaCheckPermission;
import java.math.BigInteger;
import java.util.List;
import org.dromara.certmuse.assessment.domain.bo.SimulationQueryBo;
import org.dromara.certmuse.assessment.domain.bo.StartSimulationSessionBo;
import org.dromara.certmuse.assessment.domain.vo.SimulationListItemVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationSetupVo;
import org.dromara.certmuse.assessment.service.SimulationService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@Tag("dev")
class SimulationControllerTest {
    @Test
    void allRoutesDeclareOnlyTheLearnerPermission() throws Exception {
        assertPermission(SimulationController.class.getMethod("setup"));
        assertPermission(SimulationController.class.getMethod("list", SimulationQueryBo.class));
        assertPermission(SimulationController.class.getMethod("detail", String.class));
        assertPermission(SimulationController.class.getMethod("preview", String.class));
        assertPermission(SimulationController.class.getMethod("start", String.class, String.class,
            StartSimulationSessionBo.class));
    }

    @Test
    void controllerUsesOnlyTheAuthenticatedUserForSetupAndStart() {
        SimulationService service = mock(SimulationService.class);
        SimulationController controller = new SimulationController(service);
        SimulationSetupVo setup = new SimulationSetupVo(null, "Asia/Shanghai", null, List.of());
        StartSimulationSessionBo command = new StartSimulationSessionBo();
        command.setExpectedRevisionId("12");
        command.setExpectedGoalVersion(BigInteger.ONE);
        when(service.setup(42L)).thenReturn(setup);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(42L);
            assertThat(controller.setup().getData()).isSameAs(setup);
            assertThat(controller.start("11", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command).getData()).isNull();
        }

        verify(service).setup(42L);
        verify(service).start(42L, "11", "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command);
    }

    @Test
    void listDelegatesTheBoundQueryAndKeepsTheRPageEnvelope() {
        SimulationService service = mock(SimulationService.class);
        SimulationController controller = new SimulationController(service);
        SimulationQueryBo query = new SimulationQueryBo();
        PageResult<SimulationListItemVo> page = PageResult.build(List.of(), 0);
        when(service.list(42L, query)).thenReturn(page);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(42L);
            assertThat(controller.list(query).getData()).isSameAs(page);
        }

        verify(service).list(42L, query);
    }

    private static void assertPermission(java.lang.reflect.Method method) {
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly("certmuse:student");
    }
}
