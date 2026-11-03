package com.cruise.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Shared wiring for services that obtain their live identity from the auth service. */
@Configuration(proxyBeanMethods = false)
@Import(RbacFilterConfiguration.class)
public class RemoteRbacConfiguration {
    @Bean
    public AccessTokenVerifier accessTokenVerifier(
            @Value("${cruise.auth.base-url:http://localhost:8081}") String authBaseUrl) {
        return new RemoteAccessTokenVerifier(authBaseUrl);
    }
}
