package com.cruise.common.security;

import com.cruise.common.enums.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationFilterTest {
    private final AccessTokenVerifier verifier = mock(AccessTokenVerifier.class);
    private final AuthorizationFilter filter = new AuthorizationFilter(verifier, new ServiceAccessPolicy());

    @ParameterizedTest @NullSource
    @ValueSource(strings = {"", "Basic abc", "Bearer ", "Bearer  token", "Bearer abc,def", "Bearer abc def"})
    void missingAndMalformedHeadersNeverReachTheController(String header) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/users");
        if (header != null) request.addHeader("Authorization", header);
        request.addHeader("X-Caller-Role", "SYSTEM_ADMIN");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Unauthorized controller invocation"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("UNAUTHENTICATED");
        verifyNoInteractions(verifier);
    }

    @Test void invalidTokensFailClosedAndAreNotEchoed() throws Exception {
        when(verifier.verify("sensitive-token")).thenReturn(Optional.empty());
        var response = execute("POST", "/api/auth/users", "sensitive-token");
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("sensitive-token");
    }

    @Test void identityOutageReturnsRetryable503WithoutExecutingBusinessLogic() throws Exception {
        when(verifier.verify("token")).thenThrow(new AccessTokenVerifier.Unavailable(new RuntimeException("private detail")));
        var response = execute("POST", "/api/activities", "token");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("AUTH_UNAVAILABLE").doesNotContain("private detail");
    }

    @Test void stripsSpoofedHeadersAndUsesVerifiedRole() throws Exception {
        var caller = new VerifiedCaller(12L, "shore.guide", Role.SHORE_EXCURSION_MANAGER);
        when(verifier.verify("token")).thenReturn(Optional.of(caller));
        var request = new MockHttpServletRequest("POST", "/api/excursions");
        request.addHeader("Authorization", "bEaReR token");
        request.addHeader("X-Caller-Role", "SYSTEM_ADMIN");
        request.addHeader("x-caller-role", "POS_STAFF");
        request.addHeader("X-Caller-Id", "1");
        var reached = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            reached.set(true);
            var http = (HttpServletRequest) req;
            assertThat(http.getHeader("X-Caller-Role")).isEqualTo("SHORE_EXCURSION_MANAGER");
            assertThat(Collections.list(http.getHeaders("x-caller-role"))).containsExactly("SHORE_EXCURSION_MANAGER");
            assertThat(http.getHeader("X-Caller-Id")).isNull();
            assertThat(Collections.list(http.getHeaderNames())).doesNotContain("X-Caller-Id");
            assertThat(http.getUserPrincipal()).isEqualTo(caller);
            assertThat(http.isUserInRole("SYSTEM_ADMIN")).isFalse();
        });
        assertThat(reached).isTrue();
    }

    @Test void wrongRoleCannotElevateUsingHeadersAndUnknownApisDenyAdmin() throws Exception {
        when(verifier.verify("staff")).thenReturn(Optional.of(new VerifiedCaller(2L, "pos", Role.POS_STAFF)));
        assertThat(execute("POST", "/api/auth/users", "staff").getStatus()).isEqualTo(403);
        when(verifier.verify("admin")).thenReturn(Optional.of(new VerifiedCaller(1L, "admin", Role.SYSTEM_ADMIN)));
        assertThat(execute("POST", "/api/undefined-management", "admin").getStatus()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/users;admin", "/api/auth//users", "/api/%61uth/users", "/api/auth/../activities", "/api/auth/./users"})
    void rejectsAmbiguousPaths(String path) throws Exception {
        assertThat(execute("POST", path, "token").getStatus()).isEqualTo(400);
        verifyNoInteractions(verifier);
    }

    @Test void healthAndPreflightRemainAvailableWithoutAnAuthDependency() throws Exception {
        var reached = new AtomicBoolean();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/activities/health"), new MockHttpServletResponse(),
                (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
        reached.set(false);
        filter.doFilter(new MockHttpServletRequest("OPTIONS", "/api/activities"), new MockHttpServletResponse(),
                (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
        verifyNoInteractions(verifier);
    }

    private MockHttpServletResponse execute(String method, String path, String token) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Caller-Role", "SYSTEM_ADMIN");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Denied request reached the controller"));
        return response;
    }
}
