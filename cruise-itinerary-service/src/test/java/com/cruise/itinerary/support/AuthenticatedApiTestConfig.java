package com.cruise.itinerary.support;

import com.cruise.common.enums.Role;
import com.cruise.common.security.*;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Explicit authenticated fixture for pre-existing domain tests; production RBAC remains active. */
@TestConfiguration(proxyBeanMethods = false)
public class AuthenticatedApiTestConfig {
    @Bean @Primary
    public AccessTokenVerifier domainTestIdentityVerifier() {
        return token -> "domain-test-admin".equals(token)
                ? Optional.of(new VerifiedCaller(1L, "domain.test", Role.SYSTEM_ADMIN)) : Optional.empty();
    }
    @Bean
    public MockMvcBuilderCustomizer authenticatedDomainRequests() {
        return builder -> builder.defaultRequest(get("/").header("Authorization", "Bearer domain-test-admin"));
    }
}
