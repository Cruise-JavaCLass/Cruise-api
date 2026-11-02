package com.cruise.auth.security;

import com.cruise.auth.service.JwtService;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** KAN-71: real signature verification and database state, with the production filter enabled. */
class UnauthorizedAccessApiTest extends RbacApiTestSupport {
    @Test void anonymousCannotCreateReadOrDeleteUsersOrReadTheRoleMatrix() throws Exception {
        long before = users.count();
        mvc.perform(post("/api/auth/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/auth/users/admin")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/auth/users/admin")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/roles")).andExpect(status().isUnauthorized());
        assertThat(users.count()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"Basic abc", "Bearer ", "Bearer garbage", "Bearer a.b.c"})
    void malformedCredentialsReturn401AndNoPrivateClaims(String header) throws Exception {
        mvc.perform(get("/api/auth/roles").header("Authorization", header))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test void expiredTamperedRefreshRevokedAndForeignIssuerTokensAreNotAccessCredentials() throws Exception {
        var admin = account(Role.SYSTEM_ADMIN);
        var expired = new JwtService("cruise-secret-key-super-secure-token-2026-auth-jwt-system", -1000, -1000, "cruise-auth-service");
        var foreignIssuer = new JwtService("cruise-secret-key-super-secure-token-2026-auth-jwt-system", 60000, 60000, "foreign-issuer");
        String token = jwt.generateAccessToken(admin);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        for (String invalid : new String[]{expired.generateAccessToken(admin), tampered,
                jwt.generateRefreshToken(admin), foreignIssuer.generateAccessToken(admin)}) {
            mvc.perform(get("/api/auth/roles").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized());
        }
        jwt.invalidateToken(token);
        mvc.perform(get("/api/auth/roles").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void disabledDeletedAndChangedRoleAccountsCannotReuseOldTokens() throws Exception {
        var admin = account(Role.SYSTEM_ADMIN);
        String header = bearer(admin);
        admin.setActive(false);
        users.saveAndFlush(admin);
        mvc.perform(get("/api/auth/roles").header("Authorization", header)).andExpect(status().isUnauthorized());
        admin.setActive(true);
        admin.setRole(Role.POS_STAFF);
        users.saveAndFlush(admin);
        mvc.perform(get("/api/auth/roles").header("Authorization", header)).andExpect(status().isUnauthorized());
        String staffToken = bearer(admin);
        users.delete(admin);
        users.flush();
        mvc.perform(get("/api/auth/permissions").header("Authorization", staffToken)).andExpect(status().isUnauthorized());
    }

    @Test void forgedUpstreamHeadersDoNotAuthenticateTheCaller() throws Exception {
        mvc.perform(post("/api/auth/users").header("X-Caller-Role", "SYSTEM_ADMIN").header("X-Caller-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
