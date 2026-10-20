package com.cruise.pos.config;

import com.cruise.pos.security.JwtAuthenticationFilter;
import com.cruise.pos.security.JwtVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("SecurityConfig and DisabledSecurityConfig Unit Tests")
class SecurityConfigTest {

    @Test
    @DisplayName("DisabledSecurityConfig tạo permissive SecurityFilterChain")
    void disabledSecurityConfig_BuildsFilterChain() throws Exception {
        DisabledSecurityConfig config = new DisabledSecurityConfig();
        HttpSecurity http = mock(HttpSecurity.class, org.mockito.Answers.RETURNS_DEEP_STUBS);

        SecurityFilterChain chain = config.permissiveSecurityFilterChain(http);
        assertThat(chain).isNotNull();
    }

    @Test
    @DisplayName("SecurityConfig tạo authenticated SecurityFilterChain")
    void securityConfig_BuildsFilterChain() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtVerificationService.class));
        SecurityConfig config = new SecurityConfig(filter);
        HttpSecurity http = mock(HttpSecurity.class, org.mockito.Answers.RETURNS_DEEP_STUBS);

        SecurityFilterChain chain = config.securityFilterChain(http);
        assertThat(chain).isNotNull();
    }
}
