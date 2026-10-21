package com.cruise.pos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtVerificationService jwtVerificationService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Request không có Authorization header đi qua filter bình thường")
    void doFilter_NoAuthHeader_PassesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Request có Bearer token hợp lệ thiết lập Authentication vào SecurityContext")
    void doFilter_ValidBearerToken_SetsAuthentication() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthPrincipal principal = new AuthPrincipal("guest1", "PASSENGER", "7012");
        when(jwtVerificationService.verify("valid-jwt-token")).thenReturn(Optional.of(principal));

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(principal);
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_PASSENGER"));
    }

    @Test
    @DisplayName("Request có Bearer token không hợp lệ không thiết lập Authentication")
    void doFilter_InvalidBearerToken_DoesNotSetAuthentication() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtVerificationService.verify("invalid-jwt-token")).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
