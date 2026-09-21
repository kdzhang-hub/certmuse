package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.certmuse.catalog.domain.bo.CompletedImportBatchQueryBo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchDetailVo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchPageVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchFilterOptionsVo;
import org.dromara.certmuse.catalog.domain.vo.ImportContextOptionsVo;
import org.dromara.certmuse.catalog.service.ImportService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class CompletedImportBatchControllerTest {

    @Test
    void routesCompletedListDetailAndFilterOptionsToTypedServiceMethods() {
        ImportService service = mock(ImportService.class);
        ImportController controller = new ImportController(service);
        var query = new CompletedImportBatchQueryBo();
        var page = new CompletedImportBatchPageVo(List.of(), 0, null);
        var options = new ImportBatchFilterOptionsVo(List.of(), List.of());
        var detail = new CompletedImportBatchDetailVo(
            "101",
            "source.jsonl",
            "knowledge_point",
            "21",
            "系统架构设计师 · 2026 考纲",
            "7",
            "管理员",
            "completed",
            OffsetDateTime.parse("2026-07-31T10:32:00+08:00"),
            1,
            1,
            0,
            0
        );
        when(service.completedBatches(query)).thenReturn(page);
        when(service.filterOptions()).thenReturn(options);
        when(service.completedBatch("101")).thenReturn(detail);

        assertThat(controller.completedBatches(query).getData()).isSameAs(page);
        assertThat(controller.filterOptions().getData()).isSameAs(options);
        assertThat(controller.completedBatch("101").getData()).isSameAs(detail);
        verify(service).completedBatches(query);
        verify(service).filterOptions();
        verify(service).completedBatch("101");
    }

    @Test
    void keepsLegacyContextOptionsWhenTypeParameterIsPresent() {
        ImportService service = mock(ImportService.class);
        ImportController controller = new ImportController(service);
        var options = new ImportContextOptionsVo(List.of(), List.of());
        when(service.contextOptions("knowledge_point", "21")).thenReturn(options);

        assertThat(controller.contextOptions("knowledge_point", "21", null).getData()).isSameAs(options);
        verify(service).contextOptions("knowledge_point", "21");
    }

    @Test
    void declaresPermissionAndOptionsParameterDispatchOnAllQueryEndpoints() throws Exception {
        Method list = ImportController.class.getMethod(
            "completedBatches",
            CompletedImportBatchQueryBo.class
        );
        Method detail = ImportController.class.getMethod("completedBatch", String.class);
        Method filterOptions = ImportController.class.getMethod("filterOptions");
        Method contextOptions = ImportController.class.getMethod(
            "contextOptions",
            String.class,
            String.class,
            String.class
        );

        assertImportPermission(list);
        assertImportPermission(detail);
        assertImportPermission(filterOptions);
        assertImportPermission(contextOptions);
        assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/imports/{id}");
        assertThat(filterOptions.getAnnotation(GetMapping.class).params()).containsExactly("!type");
        assertThat(contextOptions.getAnnotation(GetMapping.class).params()).containsExactly("type");
    }

    @Test
    void returnsImportErrorShapeForInvalidBoundQueryValues() throws Exception {
        ImportService service = mock(ImportService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ImportController(service))
            .setControllerAdvice(new ImportExceptionHandler())
            .setValidator(validator)
            .build();

        mvc.perform(get("/api/admin/imports").param("pageSize", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data.errorCode").value("IMPORT_CONTEXT_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("pageSize"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("OUT_OF_RANGE"));
    }

    private static void assertImportPermission(Method method) {
        assertThat(method.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("certmuse:catalog:import");
    }
}
