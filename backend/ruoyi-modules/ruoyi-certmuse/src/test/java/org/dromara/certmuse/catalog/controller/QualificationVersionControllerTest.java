package org.dromara.certmuse.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.dev33.satoken.annotation.SaCheckPermission;
import java.lang.reflect.Method;
import org.dromara.certmuse.catalog.domain.bo.QualificationQueryBo;
import org.dromara.certmuse.catalog.domain.bo.QualificationWriteBo;
import org.dromara.certmuse.catalog.domain.bo.SyllabusVersionWriteBo;
import org.dromara.certmuse.catalog.service.QualificationVersionService;
import org.dromara.certmuse.catalog.domain.vo.QualificationVersionErrorVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag("dev")
class QualificationVersionControllerTest {
    @Test
    void exposesAllSevenM01RoutesWithFrozenPermissionsAndAuditLogs() throws Exception {
        new QualificationVersionController(mock(QualificationVersionService.class));
        assertPermission("list", "certmuse:catalog:subject:list", QualificationQueryBo.class);
        assertPermission("createQualification", "certmuse:catalog:subject:add", String.class, QualificationWriteBo.class);
        assertPermission("updateQualification", "certmuse:catalog:subject:edit", String.class, String.class, QualificationWriteBo.class);
        assertPermission("deleteQualification", "certmuse:catalog:subject:remove", String.class, String.class);
        assertPermission("createVersion", "certmuse:catalog:subject:add", String.class, String.class, SyllabusVersionWriteBo.class);
        assertPermission("updateVersion", "certmuse:catalog:subject:edit", String.class, String.class, String.class, SyllabusVersionWriteBo.class);
        assertPermission("deleteVersion", "certmuse:catalog:subject:remove", String.class, String.class, String.class);
        for (String name : java.util.List.of("createQualification", "updateQualification", "deleteQualification", "createVersion", "updateVersion", "deleteVersion")) {
            assertThat(method(name).getAnnotation(Log.class)).isNotNull();
            assertThat(java.util.Arrays.stream(method(name).getParameters())
                .anyMatch(parameter -> parameter.getAnnotation(RequestHeader.class) != null)).isTrue();
        }
    }

    @Test
    void delegatesEveryQualificationAndSyllabusOperationToTheService() {
        QualificationVersionService service = mock(QualificationVersionService.class);
        QualificationVersionController controller = new QualificationVersionController(service);
        QualificationQueryBo query = mock(QualificationQueryBo.class);
        QualificationWriteBo qualification = mock(QualificationWriteBo.class);
        SyllabusVersionWriteBo version = mock(SyllabusVersionWriteBo.class);

        controller.list(query);
        controller.createQualification("request-1", qualification);
        controller.updateQualification("100", "request-2", qualification);
        controller.deleteQualification("100", "request-3");
        controller.createVersion("100", "request-4", version);
        controller.updateVersion("100", "200", "request-5", version);
        controller.deleteVersion("100", "200", "request-6");

        org.mockito.Mockito.verify(service).list(query);
        org.mockito.Mockito.verify(service).createQualification("request-1", qualification);
        org.mockito.Mockito.verify(service).updateQualification("100", "request-2", qualification);
        org.mockito.Mockito.verify(service).deleteQualification("100", "request-3");
        org.mockito.Mockito.verify(service).createVersion("100", "request-4", version);
        org.mockito.Mockito.verify(service).updateVersion("100", "200", "request-5", version);
        org.mockito.Mockito.verify(service).deleteVersion("100", "200", "request-6");
    }

    private static QualificationVersionErrorVo error(R<QualificationVersionErrorVo> response) { return response.getData(); }

    private static void assertPermission(String name, String permission, Class<?>... parameters) throws Exception {
        assertThat(QualificationVersionController.class.getMethod(name, parameters)
            .getAnnotation(SaCheckPermission.class).value()).containsExactly(permission);
    }

    private static Method method(String name) {
        return java.util.Arrays.stream(QualificationVersionController.class.getMethods())
            .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
    }
}
