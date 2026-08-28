package org.dromara.web.config;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.common.core.enums.EntryType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces the ADMIN entry on management application API namespaces. */
@Configuration
public class AdminEntryWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response,
                                     Object handler) {
                StpUtil.checkLogin();
                LoginHelper.checkEntryType(EntryType.ADMIN);
                return true;
            }
        })
            .addPathPatterns(
                "/system/**", "/monitor/**", "/tool/**", "/workflow/**",
                "/es/**", "/snail-ai/**", "/api/admin/**"
            )
            .excludePathPatterns("/system/user/getInfo", "/system/user/profile/**");
    }
}
