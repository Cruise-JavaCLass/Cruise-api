package com.cruise.activity.security;

import com.cruise.activity.repository.OnboardActivityRepository;
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
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class CrossRoleActivityApiTest {
    @Autowired MockMvc mvc;
    @Autowired OnboardActivityRepository activities;
    @MockitoBean AccessTokenVerifier verifier;

    @ParameterizedTest @EnumSource(Role.class)
    void onlyActivityManagersOpsAndAdminCanCreateActivities(Role role) throws Exception {
        when(verifier.verify("role-token")).thenReturn(Optional.of(new VerifiedCaller(5L, "actor", role)));
        boolean allowed = Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.ACTIVITY_MANAGER).contains(role);
        long before = activities.count();
        mvc.perform(post("/api/activities").header("Authorization", "Bearer role-token")
                        .header("X-Caller-Role", "SYSTEM_ADMIN").contentType(MediaType.APPLICATION_JSON).content("""
                        {"tourId":777,"title":"RBAC Activity","location":"Deck 7","activityDate":"2027-01-01",
                         "startTime":"09:00:00","endTime":"10:00:00","maxCapacity":20,"price":0,"isIncluded":true}
                        """))
                .andExpect(status().is(allowed ? 201 : 403));
        assertThat(activities.count()).isEqualTo(before + (allowed ? 1 : 0));
    }

    @Test void directServiceRequestsRejectAnonymousSpoofedHeadersAndAuthOutages() throws Exception {
        mvc.perform(get("/api/activities/tour/777").header("X-Caller-Role", "SYSTEM_ADMIN"))
                .andExpect(status().isUnauthorized());
        when(verifier.verify("outage")).thenThrow(new AccessTokenVerifier.Unavailable(new RuntimeException()));
        mvc.perform(get("/api/activities/tour/777").header("Authorization", "Bearer outage"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/activities/health")).andExpect(status().isOk());
    }
}
