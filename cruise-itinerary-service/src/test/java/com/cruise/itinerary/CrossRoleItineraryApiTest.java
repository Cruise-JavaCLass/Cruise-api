package com.cruise.itinerary;

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

@SpringBootTest @AutoConfigureMockMvc
class CrossRoleItineraryApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccessTokenVerifier verifier;

    @ParameterizedTest @EnumSource(Role.class)
    void allRolesCanReadButOnlyAuthorizedFleetRolesReachMutationValidation(Role role) throws Exception {
        when(verifier.verify("token")).thenReturn(Optional.of(new VerifiedCaller(5L, "actor", role)));
        mvc.perform(get("/api/itinerary/ships").header("Authorization", "Bearer token")).andExpect(status().isOk());
        boolean allowed = Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.ITINERARY_COORDINATOR).contains(role);
        mvc.perform(post("/api/itinerary/ships").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(allowed ? 400 : 403));
    }
    @Test void anonymousDirectServiceAccessIsDenied() throws Exception {
        mvc.perform(get("/api/itinerary/ships")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/itinerary/health")).andExpect(status().isOk());
    }
}
