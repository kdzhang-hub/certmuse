package org.dromara.certmuse.shared.web;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.dromara.common.core.domain.R;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class CertMuseRequestValidationAdviceTest {

    @Test
    void mapsPathVariableTypeMismatchToAFieldLevel400() throws Exception {
        mvc().perform(get("/test/request-validation/type/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data.errorCode").value("REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.retryable").value(false))
            .andExpect(jsonPath("$.data.traceId").doesNotExist())
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("id"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void mapsHandlerMethodParameterValidationTo400() throws Exception {
        mvc().perform(get("/test/request-validation/method"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data.errorCode").value("REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("pageNum"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("OUT_OF_RANGE"));
    }

    @Test
    void mapsConstraintViolationsTo400WithTheRequestFieldName() throws Exception {
        mvc().perform(get("/test/request-validation/constraint"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data.errorCode").value("REQUEST_INVALID"))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("name"))
            .andExpect(jsonPath("$.data.fieldErrors[0].code").value("REQUIRED"));
    }

    @Test
    void keepsReturnValueValidationAsASafe500() throws Exception {
        mvc().perform(get("/test/request-validation/return-value"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.data.errorCode").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.data.traceId").isNotEmpty())
            .andExpect(jsonPath("$.msg").value("系统暂时无法处理请求"));
    }

    private static MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new ValidationController())
            .setControllerAdvice(new CertMuseExceptionHandler())
            .build();
    }

    @Validated
    @RestController
    @RequestMapping("/test/request-validation")
    static class ValidationController {
        @GetMapping("/type/{id}")
        R<Void> type(@PathVariable long id) {
            return R.ok();
        }

        @GetMapping("/method")
        R<Void> method() throws NoSuchMethodException {
            var method = ValidationController.class.getDeclaredMethod("validatedMethod", int.class);
            var parameter = new MethodParameter(method, 0);
            parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
            var error = new DefaultMessageSourceResolvable(
                new String[] {"Positive.pageNum", "Positive"}, "页码必须为正数");
            var result = new ParameterValidationResult(
                parameter, 0, java.util.List.of(error), null, null, null, null);
            throw new HandlerMethodValidationException(MethodValidationResult.create(
                this, method, java.util.List.of(result)));
        }

        void validatedMethod(@Positive int pageNum) {
        }

        @GetMapping("/constraint")
        R<Void> constraint() throws NoSuchMethodException {
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                var method = ValidationController.class.getDeclaredMethod(
                    "validatedConstraintMethod", String.class);
                var violations = factory.getValidator().forExecutables()
                    .validateParameters(this, method, new Object[] {""});
                throw new ConstraintViolationException(violations);
            }
        }

        void validatedConstraintMethod(@NotBlank String name) {
        }

        @GetMapping("/return-value")
        R<Void> returnValue() throws NoSuchMethodException {
            var method = ValidationController.class.getDeclaredMethod("validatedReturnValue");
            var returnValue = new MethodParameter(method, -1);
            var error = new DefaultMessageSourceResolvable(
                new String[] {"NotNull.returnValue", "NotNull"}, "返回值不能为空");
            var result = new ParameterValidationResult(
                returnValue, null, java.util.List.of(error), null, null, null, null);
            throw new HandlerMethodValidationException(MethodValidationResult.create(
                this, method, java.util.List.of(result)));
        }

        @jakarta.validation.constraints.NotNull
        String validatedReturnValue() {
            return null;
        }
    }

}
