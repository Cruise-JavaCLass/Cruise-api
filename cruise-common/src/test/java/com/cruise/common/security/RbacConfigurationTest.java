package com.cruise.common.security;

import com.cruise.common.enums.Role;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class RbacConfigurationTest {
    @Test
    void localIdentityWiringUsesOnlyTheProvidedVerifierAndEnforcesItsRole() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AccessTokenVerifier.class,
                    () -> token -> Optional.of(new VerifiedCaller(7L, "activity.manager", Role.ACTIVITY_MANAGER)));
            context.register(RbacFilterConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AccessTokenVerifier.class)).hasSize(1);
            var registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
            assertThat(registration.getOrder()).isLessThan(Ordered.HIGHEST_PRECEDENCE + 100);
            var request = new MockHttpServletRequest("POST", "/api/activities");
            request.addHeader("Authorization", "Bearer local-token");
            var reached = new AtomicBoolean();
            registration.getFilter().doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> reached.set(true));
            assertThat(reached).isTrue();

            request = new MockHttpServletRequest("POST", "/api/auth/users");
            request.addHeader("Authorization", "Bearer local-token");
            var denied = new MockHttpServletResponse();
            reached.set(false);
            registration.getFilter().doFilter(request, denied, (req, res) -> reached.set(true));
            assertThat(reached).isFalse();
            assertThat(denied.getStatus()).isEqualTo(403);
        }
    }

    @Test
    void remoteIdentityWiringRegistersOneProtectedFilterWithoutAnAuthNetworkDependency() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("rbac-test",
                    Map.of("cruise.auth.base-url", "http://127.0.0.1:1")));
            context.register(RemoteRbacConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(FilterRegistrationBean.class)).hasSize(1);
            assertThat(context.getBean(AccessTokenVerifier.class)).isInstanceOf(RemoteAccessTokenVerifier.class);
            var registration = context.getBean(FilterRegistrationBean.class);
            var response = new MockHttpServletResponse();
            var reached = new AtomicBoolean();
            registration.getFilter().doFilter(new MockHttpServletRequest("GET", "/api/activities/tour/7"),
                    response, (req, res) -> reached.set(true));
            assertThat(reached).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }
}
