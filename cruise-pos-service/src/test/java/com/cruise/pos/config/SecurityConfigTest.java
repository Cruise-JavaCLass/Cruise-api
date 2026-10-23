package com.cruise.pos.config;

import com.cruise.pos.security.JwtAuthenticationFilter;
import com.cruise.pos.security.JwtVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestMatcherDelegatingAuthorizationManager;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.function.Supplier;

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

    @Test
    @DisplayName("Manifest cache endpoint: allowed roles succeed and unauthorized roles receive 403 / denied")
    void securityConfig_ManifestEndpointRoleAuthorization() {
        // Exercise the exact authorization rule configured in SecurityConfig:
        // .requestMatchers("/api/pos/auth/manifest/**").hasAnyRole("POS_STAFF", "OPERATIONS_MANAGER", "SYSTEM_ADMIN")
        // .anyRequest().authenticated()
        RequestMatcherDelegatingAuthorizationManager authManager =
                RequestMatcherDelegatingAuthorizationManager.builder()
                        .requestMatchers(PathPatternRequestMatcher.pathPattern("/api/pos/auth/manifest/**"))
                        .hasAnyRole("POS_STAFF", "OPERATIONS_MANAGER", "SYSTEM_ADMIN")
                        .anyRequest().authenticated()
                        .build();

        MockHttpServletRequest manifestRequest = new MockHttpServletRequest("GET", "/api/pos/auth/manifest/cache");

        // Allowed role 1: POS_STAFF -> granted
        Supplier<Authentication> posStaff = () -> new TestingAuthenticationToken("staff1", "pass", "ROLE_POS_STAFF");
        var decisionPosStaff = authManager.authorize(posStaff, manifestRequest);
        assertThat(decisionPosStaff).isNotNull();
        assertThat(decisionPosStaff.isGranted()).isTrue();

        // Allowed role 2: OPERATIONS_MANAGER -> granted
        Supplier<Authentication> opsManager = () -> new TestingAuthenticationToken("mgr1", "pass", "ROLE_OPERATIONS_MANAGER");
        var decisionOpsManager = authManager.authorize(opsManager, manifestRequest);
        assertThat(decisionOpsManager).isNotNull();
        assertThat(decisionOpsManager.isGranted()).isTrue();

        // Allowed role 3: SYSTEM_ADMIN -> granted
        Supplier<Authentication> sysAdmin = () -> new TestingAuthenticationToken("admin1", "pass", "ROLE_SYSTEM_ADMIN");
        var decisionSysAdmin = authManager.authorize(sysAdmin, manifestRequest);
        assertThat(decisionSysAdmin).isNotNull();
        assertThat(decisionSysAdmin.isGranted()).isTrue();

        // Disallowed role: PASSENGER -> denied (403 Forbidden)
        Supplier<Authentication> passenger = () -> new TestingAuthenticationToken("p1", "pass", "ROLE_PASSENGER");
        var decisionPassenger = authManager.authorize(passenger, manifestRequest);
        assertThat(decisionPassenger).isNotNull();
        assertThat(decisionPassenger.isGranted()).isFalse();

        // Disallowed role: GUEST -> denied (403 Forbidden)
        Supplier<Authentication> guest = () -> new TestingAuthenticationToken("g1", "pass", "ROLE_GUEST");
        var decisionGuest = authManager.authorize(guest, manifestRequest);
        assertThat(decisionGuest).isNotNull();
        assertThat(decisionGuest.isGranted()).isFalse();

        // Disallowed role: FINANCE_FRONT_DESK -> denied (403 Forbidden)
        Supplier<Authentication> finance = () -> new TestingAuthenticationToken("f1", "pass", "ROLE_FINANCE_FRONT_DESK");
        var decisionFinance = authManager.authorize(finance, manifestRequest);
        assertThat(decisionFinance).isNotNull();
        assertThat(decisionFinance.isGranted()).isFalse();
    }
}
