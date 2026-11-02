package com.cruise.pos.security;

import com.cruise.common.enums.Role;
import com.cruise.common.security.*;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "pos.security.enabled=true") @AutoConfigureMockMvc
class CrossRolePosApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccessTokenVerifier verifier;

    @ParameterizedTest @EnumSource(Role.class)
    void cashiersCannotSettleFoliosAndFinanceCannotCreateSales(Role role) throws Exception {
        when(verifier.verify("token")).thenReturn(Optional.of(new VerifiedCaller(5L, "actor", role)));
        boolean sale = Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.POS_STAFF).contains(role);
        mvc.perform(post("/api/pos/transactions").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(sale ? 400 : 403));
        boolean finance = Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.FINANCE_FRONT_DESK).contains(role);
        mvc.perform(put("/api/pos/folios/A101/credit-limit").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(finance ? 400 : 403));
    }
    @Test void healthIsPublicButInternalApisAreProtectedWithSpringSecurityEnabled() throws Exception {
        mvc.perform(get("/api/pos/health")).andExpect(status().isOk());
        mvc.perform(get("/api/pos/folios")).andExpect(status().isUnauthorized());
    }
}
