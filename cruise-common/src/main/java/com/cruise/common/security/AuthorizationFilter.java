package com.cruise.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** Service boundary authorization, including direct port access that bypasses the gateway. */
public final class AuthorizationFilter extends OncePerRequestFilter {
    private static final Logger AUDIT = LoggerFactory.getLogger("com.cruise.security.audit");
    private final AccessTokenVerifier verifier;
    private final AccessPolicy policy;

    public AuthorizationFilter(AccessTokenVerifier verifier, AccessPolicy policy) {
        this.verifier = verifier;
        this.policy = policy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/") && !path.equals("/api")) {
            chain.doFilter(request, response);
            return;
        }
        // Reject ambiguous paths instead of letting routing and policy normalization disagree.
        if (path.contains("%") || path.contains(";") || path.contains("\\") || path.contains("//")
                || path.matches(".*/\\.\\.?(/.*)?")) {
            reject(request, response, 400, "INVALID_PATH", "Ambiguous API path", null);
            return;
        }
        String method = request.getMethod();
        if ("OPTIONS".equals(method)) {
            chain.doFilter(request, response);
            return;
        }
        AccessRequirement rule = policy.requirement(method, path);
        if (rule.mode() == AccessRequirement.Mode.PUBLIC) {
            chain.doFilter(new CallerRequest(request, null), response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.matches("(?i)Bearer [^\\s,]{1,8192}")) {
            reject(request, response, 401, "UNAUTHENTICATED", "Missing or invalid Authorization header", null);
            return;
        }
        Optional<VerifiedCaller> result;
        try {
            result = verifier.verify(header.substring(7));
        } catch (AccessTokenVerifier.Unavailable ex) {
            reject(request, response, 503, "AUTH_UNAVAILABLE", "Identity service unavailable; retry later", null);
            return;
        }
        if (result.isEmpty()) {
            reject(request, response, 401, "UNAUTHENTICATED", "Invalid or expired session token", null);
            return;
        }
        VerifiedCaller caller = result.get();
        if (rule.mode() == AccessRequirement.Mode.DENY
                || (rule.mode() == AccessRequirement.Mode.PERMISSION
                && !RolePermissions.allows(caller.role(), rule.permission()))) {
            reject(request, response, 403, "FORBIDDEN", "Your role does not have this permission", caller);
            return;
        }
        request.setAttribute(VerifiedCaller.ATTRIBUTE, caller);
        try {
            chain.doFilter(new CallerRequest(request, caller), response);
        } finally {
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                AUDIT.info("event=api_write userId={} role={} method={} path={} status={}",
                        caller.userId(), caller.role(), method, path, response.getStatus());
            }
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, int status,
                        String code, String message, VerifiedCaller caller) throws IOException {
        AUDIT.warn("event=access_denied userId={} role={} method={} path={} status={} code={}",
                caller == null ? null : caller.userId(), caller == null ? null : caller.role(),
                request.getMethod(), request.getRequestURI(), status, code);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write("{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    /** Override spoofable role headers. Auth user IDs are deliberately not passenger IDs. */
    private static final class CallerRequest extends HttpServletRequestWrapper {
        private final VerifiedCaller caller;
        CallerRequest(HttpServletRequest request, VerifiedCaller caller) { super(request); this.caller = caller; }
        @Override public String getHeader(String name) {
            if ("X-Caller-Role".equalsIgnoreCase(name)) return caller == null ? null : caller.role().name();
            if ("X-Caller-Id".equalsIgnoreCase(name)) return null;
            return super.getHeader(name);
        }
        @Override public Enumeration<String> getHeaders(String name) {
            if ("X-Caller-Role".equalsIgnoreCase(name) || "X-Caller-Id".equalsIgnoreCase(name)) {
                String value = getHeader(name);
                return Collections.enumeration(value == null ? java.util.List.of() : java.util.List.of(value));
            }
            return super.getHeaders(name);
        }
        @Override public Enumeration<String> getHeaderNames() {
            var names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.removeIf(n -> "X-Caller-Role".equalsIgnoreCase(n) || "X-Caller-Id".equalsIgnoreCase(n));
            if (caller != null) names.add("X-Caller-Role");
            return Collections.enumeration(names);
        }
        @Override public Principal getUserPrincipal() { return caller; }
        @Override public String getRemoteUser() { return caller == null ? null : caller.username(); }
        @Override public boolean isUserInRole(String role) { return caller != null && caller.role().name().equals(role); }
    }
}
