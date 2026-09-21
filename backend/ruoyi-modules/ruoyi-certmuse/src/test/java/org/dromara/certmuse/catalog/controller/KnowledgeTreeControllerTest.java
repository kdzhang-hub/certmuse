package org.dromara.certmuse.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.LocalDate;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.certmuse.catalog.domain.bo.SyllabusPublishedDateUpdateBo;
import org.dromara.certmuse.catalog.service.KnowledgeTreeService;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag("dev")
class KnowledgeTreeControllerTest {

    @Test
    void deletesKnowledgeTreeUsingTheExistingEndpointAndPermission() throws Exception {
        KnowledgeTreeService service = Mockito.mock(KnowledgeTreeService.class);
        KnowledgeTreeController controller = new KnowledgeTreeController(service);

        var response = controller.deleteSyllabusVersion("21");

        verify(service).deleteKnowledgeTree("21");
        assertThat(response.getCode()).isEqualTo(200);

        Method method = KnowledgeTreeController.class.getMethod("deleteSyllabusVersion", String.class);
        assertThat(method.getAnnotation(DeleteMapping.class).value()).containsExactly("/{syllabusVersionId}");
        assertThat(method.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("certmuse:catalog:knowledge:remove");
        Log log = method.getAnnotation(Log.class);
        assertThat(log.title()).isEqualTo("知识点树");
        assertThat(log.businessType()).isEqualTo(BusinessType.DELETE);
    }

    @Test
    void delegatesSyllabusSearchAndKnowledgeTreeQueries() {
        KnowledgeTreeService service = Mockito.mock(KnowledgeTreeService.class);
        KnowledgeTreeController controller = new KnowledgeTreeController(service);

        controller.syllabuses("架构", "100", "第二版", "available", 2, 20);
        controller.knowledgeTree("200");

        verify(service).syllabuses("架构", "100", "第二版", "available", 2, 20);
        verify(service).knowledgeTree("200");
    }

    @Test
    void publishedDateEndpointUsesTheDedicatedPermissionAndRequestIdHeader() throws Exception {
        KnowledgeTreeService service = Mockito.mock(KnowledgeTreeService.class);
        SyllabusPublishedDateController controller = new SyllabusPublishedDateController(service);
        SyllabusPublishedDateUpdateBo command = new SyllabusPublishedDateUpdateBo();
        command.setPublishedDate(LocalDate.of(2026, 8, 13));

        var response = controller.updateSyllabusPublishedDate("21", "adf7bbf7-7f3f-4632-9e06-78b6d79b047d", command);

        verify(service).updateSyllabusPublishedDate("21", "adf7bbf7-7f3f-4632-9e06-78b6d79b047d", command);
        assertThat(response.getCode()).isEqualTo(200);

        Method method = SyllabusPublishedDateController.class.getMethod(
            "updateSyllabusPublishedDate", String.class, String.class, SyllabusPublishedDateUpdateBo.class);
        assertThat(method.getAnnotation(PutMapping.class).value()).containsExactly("/{syllabusVersionId}/published-date");
        assertThat(method.getParameters()[1].getAnnotation(RequestHeader.class).value()).isEqualTo("X-Request-Id");
        assertThat(method.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("certmuse:catalog:knowledge:edit");
        Log log = method.getAnnotation(Log.class);
        assertThat(log.title()).isEqualTo("考纲发布日期");
        assertThat(log.businessType()).isEqualTo(BusinessType.UPDATE);
    }
}
