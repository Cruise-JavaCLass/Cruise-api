package com.cruise.itinerary.config;

import com.cruise.common.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class RbacConfig {
    @Bean
    public AccessTokenVerifier accessTokenVerifier(
            @Value("${cruise.auth.base-url:http://localhost:8081}") String authBaseUrl) {
        return new RemoteAccessTokenVerifier(authBaseUrl);
    }

    @Bean
    public FilterRegistrationBean<AuthorizationFilter> authorizationFilter(AccessTokenVerifier verifier) {
        var registration = new FilterRegistrationBean<>(new AuthorizationFilter(verifier, new ServiceAccessPolicy()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
