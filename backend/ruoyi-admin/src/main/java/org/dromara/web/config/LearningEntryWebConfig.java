package org.dromara.web.config;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.common.core.enums.EntryType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Enforces the LEARNING entry on learner application API namespaces. */
@Configuration
public class LearningEntryWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response,
                                     Object handler) {
                StpUtil.checkLogin();
                LoginHelper.checkEntryType(EntryType.LEARNING);
                return true;
            }
        }).addPathPatterns("/api/learning/**", "/api/assessment/diagnostics/**");
    }
}
