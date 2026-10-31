package com.prgate.users.interfaces.rest;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    private final InternalServiceAuthInterceptor internalServiceAuthInterceptor;

    public WebMvcConfiguration(InternalServiceAuthInterceptor internalServiceAuthInterceptor) {
        this.internalServiceAuthInterceptor = internalServiceAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalServiceAuthInterceptor).addPathPatterns("/internal/**");
    }
}
