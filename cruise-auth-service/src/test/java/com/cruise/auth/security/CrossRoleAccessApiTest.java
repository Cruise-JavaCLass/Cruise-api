package com.cruise.auth.security;

import com.cruise.auth.repository.IdentityAuditRepository;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** KAN-72: all eight real JWT roles against the production auth HTTP boundary. */
class CrossRoleAccessApiTest extends RbacApiTestSupport {
    @Autowired private IdentityAuditRepository audit;

    @ParameterizedTest @EnumSource(Role.class)
    void onlySystemAdminCanReadOrMutateUserAdministration(Role role) throws Exception {
        String header = bearer(account(role));
        int expected = role == Role.SYSTEM_ADMIN ? 200 : 403;
        mvc.perform(get("/api/auth/roles").header("Authorization", header)
                        .header("X-Caller-Role", "SYSTEM_ADMIN"))
                .andExpect(status().is(expected));
        long before = users.count();
        mvc.perform(post("/api/auth/users").header("Authorization", header).header("X-Caller-Role", "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(role == Role.SYSTEM_ADMIN ? 400 : 403));
        assertThat(users.count()).isEqualTo(before);
        mvc.perform(get("/api/auth/permissions").header("Authorization", header))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }

    @Test void adminCreatesAndDeletesUserWithTransactionalAuditAndNoPasswordDisclosure() throws Exception {
        var admin = account(Role.SYSTEM_ADMIN);
        String header = bearer(admin);
        long before = audit.count();
        String payload = """
            {"username":"rbac.new.staff","password":"Secret#2026Test","fullName":"New Staff",
             "email":"new.staff@rbac.test","role":"ACTIVITY_MANAGER"}
            """;
        mvc.perform(post("/api/auth/users").header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.role").value("ACTIVITY_MANAGER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        var created = users.findByUsername("rbac.new.staff").orElseThrow();
        assertThat(created.getPassword()).startsWith("$2").doesNotContain("Secret#2026Test");
        String staffToken = bearer(created);
        mvc.perform(delete("/api/auth/users/rbac.new.staff").header("Authorization", header)).andExpect(status().isOk());
        assertThat(users.findByUsername("rbac.new.staff")).isEmpty();
        assertThat(audit.count()).isEqualTo(before + 2);
        assertThat(audit.findAll().stream().filter(e -> e.getTargetUsername().equals("rbac.new.staff")))
                .allSatisfy(e -> assertThat(e.getActorUserId()).isEqualTo(admin.getId()));
        mvc.perform(get("/api/auth/permissions").header("Authorization", staffToken)).andExpect(status().isUnauthorized());
    }

    @Test void nonAdminCannotDeleteAnotherRoleAndAdminCannotUseAnUndefinedApi() throws Exception {
        var target = account(Role.SYSTEM_ADMIN);
        var cashier = account(Role.POS_STAFF);
        mvc.perform(delete("/api/auth/users/" + target.getUsername()).header("Authorization", bearer(cashier)))
                .andExpect(status().isForbidden());
        assertThat(users.existsByUsername(target.getUsername())).isTrue();
        mvc.perform(get("/api/undefined-admin-route").header("Authorization", bearer(target)))
                .andExpect(status().isForbidden());
    }
}
